/*-
 *   BSD LICENSE
 *
 *   Copyright(c) 2010-2015 Intel Corporation. All rights reserved.
 *   All rights reserved.
 *
 *   Redistribution and use in source and binary forms, with or without
 *   modification, are permitted provided that the following conditions
 *   are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Intel Corporation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *   OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *   DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *   THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *   OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <stdint.h>
#include <inttypes.h>
#include <rte_eal.h>
#include <rte_ethdev.h>
#include <rte_cycles.h>
#include <rte_lcore.h>
#include <rte_mbuf.h>
#include <rte_ether.h>
#include <rte_ip.h>
#include <rte_UDP.h>

#define RX_RING_SIZE 128
#define TX_RING_SIZE 512

#define NUM_MBUFS 8191
#define MBUF_CACHE_SIZE 250
#define BURST_SIZE 32

#define SEND_PORT 0
#define RECV_PORT 0

#define IP_DN_FRAGMENT_FLAG 0x0040
#define IPDEFTTL 64

#define BOND_IP_1   10
#define BOND_IP_2   211
#define BOND_IP_3   55
#define BOND_IP_4   10

static const struct rte_eth_conf port_conf_default = {
	.rxmode = { .max_rx_pkt_len = ETHER_MAX_LEN }
};

/*
 * Construct UDP packets with DPDK according to the definition of 
 * UDP/IP/Ethernet header.
 */
static inline void
construct_udp_pkt(struct rte_mbuf *pkt, const char *content, int content_len)
{
    struct ether_hdr *ehdr;
    struct ipv4_hdr *ihdr;
    struct UDP_hdr *uhdr;
	void *payload;
    struct ether_addr d_eaddr, s_eaddr;
    uint32_t bond_ip =
        BOND_IP_1 | (BOND_IP_2 << 8) | (BOND_IP_3 << 16) | (BOND_IP_4 << 24);

    /* Initialize header pointers */
    ehdr = rte_pktmbuf_mtod(pkt, struct ether_hdr *);
    ihdr = rte_pktmbuf_mtod_offset(pkt, struct ipv4_hdr *,
                                   sizeof(struct ether_hdr));
    uhdr = rte_pktmbuf_mtod_offset(pkt, struct UDP_hdr *,
                                   sizeof(struct ether_hdr) +
                                       sizeof(struct ipv4_hdr));

    /* Fill headers */
    rte_eth_macaddr_get(SEND_PORT, &d_eaddr);
    rte_eth_macaddr_get(RECV_PORT, &s_eaddr);
    ehdr->d_addr = d_eaddr;
    ehdr->s_addr = s_eaddr;
    ehdr->ether_type = rte_cpu_to_be_16(RTE_ETHER_TYPE_IPV4);

    ihdr->version_ihl = IPVERSION << 4 | sizeof(ihdr) / RTE_IPV4_IHL_MULTIPLIER;
	ihdr->type_of_service = 0;
	ihdr->total_length = rte_cpu_to_be_16(pkt->data_len);
	ihdr->packet_id = 0;
	ihdr->fragment_offset = IP_DN_FRAGMENT_FLAG;
	ihdr->time_to_live = IPDEFTTL;
	ihdr->next_proto_id = IPPROTO_UDP;
	ihdr->hdr_checksum = 0;
	ihdr->hdr_checksum = rte_ipv4_cksum(ihdr);
	ihdr->src_addr = bond_ip;
	ihdr->dst_addr = bond_ip;

	uhdr->src_port = 0;
	uhdr->dst_port = 0;
	uhdr->dgram_len = rte_cpu_to_be_16(pkt->data_len);
	uhdr->dgram_cksum = 0;

	/* Fill content */
	payload = rte_pktmbuf_mtod_offset(pkt, void *,
										sizeof(struct ether_hdr) +
											sizeof(struct ipv4_hdr) +
											sizeof(struct UDP_hdr));
	memcpy(payload, content, content_len);
}

/*
 * Initializes a given port using global settings and with the RX buffers
 * coming from the mbuf_pool passed as a parameter.
 */
static inline int
port_init(uint8_t port, struct rte_mempool *mbuf_pool)
{
	struct rte_eth_conf port_conf = port_conf_default;
	const uint16_t rx_rings = 1, tx_rings = 1;
	int retval;
	uint16_t q;

	if (port >= rte_eth_dev_count())
		return -1;

	/* Configure the Ethernet device. */
	retval = rte_eth_dev_configure(port, rx_rings, tx_rings, &port_conf);
	if (retval != 0)
		return retval;

	/* Allocate and set up 1 RX queue per Ethernet port. */
	for (q = 0; q < rx_rings; q++) {
		retval = rte_eth_rx_queue_setup(port, q, RX_RING_SIZE,
				rte_eth_dev_socket_id(port), NULL, mbuf_pool);
		if (retval < 0)
			return retval;
	}

	/* Allocate and set up 1 TX queue per Ethernet port. */
	for (q = 0; q < tx_rings; q++) {
		retval = rte_eth_tx_queue_setup(port, q, TX_RING_SIZE,
				rte_eth_dev_socket_id(port), NULL);
		if (retval < 0)
			return retval;
	}

	/* Start the Ethernet port. */
	retval = rte_eth_dev_start(port);
	if (retval < 0)
		return retval;

	/* Display the port MAC address. */
	struct ether_addr addr;
	rte_eth_macaddr_get(port, &addr);
	printf("Port %u MAC: %02" PRIx8 " %02" PRIx8 " %02" PRIx8
			   " %02" PRIx8 " %02" PRIx8 " %02" PRIx8 "\n",
			(unsigned)port,
			addr.addr_bytes[0], addr.addr_bytes[1],
			addr.addr_bytes[2], addr.addr_bytes[3],
			addr.addr_bytes[4], addr.addr_bytes[5]);

	/* Enable RX in promiscuous mode for the Ethernet device. */
	rte_eth_promiscuous_enable(port);

	return 0;
}

/*
 * The lcore main. This is the main thread that does the work, reading from
 * an input port and writing to an output port.
 */
static __attribute__((noreturn)) void
lcore_main(struct rte_mempool *mbuf_pool)
{
	const uint8_t nb_ports = rte_eth_dev_count();
	uint8_t port;
    struct rte_mbuf *pkt;
    char pkt_content[] = "hello world";

	/*
	 * Check that the port is on the same NUMA node as the polling thread
	 * for best performance.
	 */
	for (port = 0; port < nb_ports; port++)
		if (rte_eth_dev_socket_id(port) > 0 &&
				rte_eth_dev_socket_id(port) !=
						(int)rte_socket_id())
			printf("WARNING, port %u is on remote NUMA node to "
					"polling thread.\n\tPerformance will "
					"not be optimal.\n", port);

	printf("\nCore %u forwarding packets. [Ctrl+C to quit]\n",
			rte_lcore_id());

    /* Allocate a packet */
    pkt = rte_pktmbuf_alloc(mbuf_pool);;
    if (pkt == NULL) 
        rte_exit(EXIT_FAILURE, "Error with mbuf initialization\n");

    /* Reserve space for headers and content */
    if (rte_pktmbuf_prepend(
            pkt, sizeof(struct ether_hdr) + sizeof(struct ipv4_hdr) +
                     sizeof(struct UDP_hdr) + sizeof(pkt_content)) == NULL)
        rte_exit(EXIT_FAILURE, "Error with mbuf prepending\n");

    /* construct UDP packet */
    construct_udp_pkt(pkt, pkt_content, sizeof(pkt_content));


	struct rte_mbuf *bufs[1];
	bufs[0] = pkt;
	/* Run until the application is quit or killed. */
	for (;;) {
		/* Send burst of TX packets from port 0 */
		const uint16_t nb_tx = rte_eth_tx_burst(SEND_PORT, 0, bufs, 1);
		printf("Send a packet\n");
		sleep(1);
	}
}

/*
 * The main function, which does initialization and calls the per-lcore
 * functions.
 */
int
main(int argc, char *argv[])
{
	struct rte_mempool *mbuf_pool;
	unsigned nb_ports;
	uint8_t portid;

	/* Initialize the Environment Abstraction Layer (EAL). */
	int ret = rte_eal_init(argc, argv);
	if (ret < 0)
		rte_exit(EXIT_FAILURE, "Error with EAL initialization\n");

	argc -= ret;
	argv += ret;

	/* Check that there is an even number of ports to send/receive on. */
	nb_ports = rte_eth_dev_count();
	// if (nb_ports < 2 || (nb_ports & 1))
	// 	rte_exit(EXIT_FAILURE, "Error: number of ports must be even\n");

	/* Creates a new mempool in memory to hold the mbufs. */
	mbuf_pool = rte_pktmbuf_pool_create("MBUF_POOL", NUM_MBUFS * nb_ports,
		MBUF_CACHE_SIZE, 0, RTE_MBUF_DEFAULT_BUF_SIZE, rte_socket_id());

	if (mbuf_pool == NULL)
		rte_exit(EXIT_FAILURE, "Cannot create mbuf pool\n");

	/* Initialize all ports. */
	for (portid = 0; portid < nb_ports; portid++)
		if (port_init(portid, mbuf_pool) != 0)
			rte_exit(EXIT_FAILURE, "Cannot init port %"PRIu8 "\n",
					portid);

	if (rte_lcore_count() > 1)
		printf("\nWARNING: Too many lcores enabled. Only 1 used.\n");

	/* Call lcore_main on the master core only. */
	lcore_main();

	return 0;
}
