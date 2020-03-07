/*
 * FILE: rdt_receiver.cc
 * DESCRIPTION: Reliable data transfer receiver.
 * NOTE: In this implementation, the packet format is laid out as 
 *       the following:
 *       
 *                      |<-        1 byte        ->|
 *                      |<-      packet info     ->|
 *       |<-  2 byte  ->|<-1 bit->|<-    7 bit   ->|<-  4 byte  ->|<-       the rest      ->|
 *       |<- checksum ->|<- EOM ->|<-payload size->|<-  seq no. ->|<-       payload       ->|
 */


#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "rdt_struct.h"
#include "rdt_receiver.h"

#define WINDOW_SIZE         10
#define MAX_MESSAGE_SIZE    10000
#define CHECKSUM_OFFSET     0
#define CHECKSUM_SIZE       2
#define INFO_OFFSET         CHECKSUM_OFFSET + CHECKSUM_SIZE
#define INFO_SIZE           1
#define SEQNUM_OFFSET       INFO_OFFSET + INFO_SIZE
#define SEQNUM_SIZE         4
#define PAYLOAD_OFFSET      SEQNUM_OFFSET + SEQNUM_SIZE

#define CHECKSUM(data)          (*(short *)&data[CHECKSUM_OFFSET])
#define END_OF_MESSAGE(data)    ((data[INFO_OFFSET] & 0X80) >> 7)
#define PAYLOAD_SIZE(data)      (data[INFO_OFFSET] & 0X7F)
#define SEQ_NO(data)            (*(int *)&data[SEQNUM_OFFSET])

struct window {
    bool valid;
    struct packet pkt;
};

static struct window *buffer;           // buffers for the inbound stream
static char *curr_msg_data;             // current message to be constructed
static int curr_msg_size = 0;           // current message size
static int seq_expected = 0;            // next seq expected inbound

static short checksum(packet *pkt)
{
    unsigned short *buf = (unsigned short *)pkt->data;
    unsigned long sum = 0;
    buf++;  /* skip checksum part */
    for (int i = 2; i < RDT_PKTSIZE; i += sizeof(unsigned short)) 
        sum += *buf++;
    while (sum >> 16) 
        sum = (sum >> 16) + (sum & 0xffff);
    return ~sum;
}

/* receiver initialization, called once at the very beginning */
void Receiver_Init()
{
    fprintf(stdout, "At %.2fs: receiver initializing ...\n", GetSimulationTime());
    curr_msg_data = (char *)malloc(MAX_MESSAGE_SIZE);
    buffer = (struct window *)malloc(WINDOW_SIZE * sizeof(struct window));
    ASSERT(curr_msg_data != NULL);
    ASSERT(buffer != 0);
    memset(buffer, '\0', WINDOW_SIZE * sizeof(struct window));
}

/* receiver finalization, called once at the very end.
   you may find that you don't need it, in which case you can leave it blank.
   in certain cases, you might want to use this opportunity to release some 
   memory you allocated in Receiver_init(). */
void Receiver_Final()
{
    fprintf(stdout, "At %.2fs: receiver finalizing ...\n", GetSimulationTime());
    free(buffer);
    free(curr_msg_data);
}

/* move data from window into message buffer, return the lastest seq received */
static int Receiver_ShrinkWindow(int seq_no)
{
    while (buffer[seq_no % WINDOW_SIZE].valid) {
        struct packet *pkt = &buffer[seq_no % WINDOW_SIZE].pkt;
        /* concatenate the new data into current constructing message */
        ASSERT(PAYLOAD_SIZE(pkt->data) > 0 && PAYLOAD_SIZE(pkt->data) <= RDT_PKTSIZE - SEQNUM_SIZE - INFO_SIZE - CHECKSUM_SIZE);
        memcpy(curr_msg_data + curr_msg_size, pkt->data + PAYLOAD_OFFSET, PAYLOAD_SIZE(pkt->data));
        curr_msg_size += PAYLOAD_SIZE(pkt->data);
        ASSERT(curr_msg_size < MAX_MESSAGE_SIZE);

        /* finish constructing a message, send it to upper layer */
        if (END_OF_MESSAGE(pkt->data)) {
            struct message *msg = (struct message*)malloc(sizeof(struct message));
            ASSERT(msg != NULL);
            msg->data = (char *)malloc(curr_msg_size);
            ASSERT(msg->data != NULL);
            msg->size = curr_msg_size;
            memcpy(msg->data, curr_msg_data, curr_msg_size);
            Receiver_ToUpperLayer(msg);
            if (msg->data != NULL) 
                free(msg->data);
            if (msg != NULL) 
                free(msg);
            curr_msg_size = 0;
            memset(curr_msg_data, '\0', curr_msg_size);
        }
        buffer[seq_no % WINDOW_SIZE].valid = false;
        seq_no++;
    }
    return --seq_no;
}

/* send ack packet */
static void Receiver_SendAck(int ack_no)
{
    struct packet *ack_pkt = (struct packet *)malloc(sizeof(struct packet));
    memset(ack_pkt->data, '\0', RDT_PKTSIZE);
    memcpy(ack_pkt->data + SEQNUM_OFFSET, &ack_no, SEQNUM_SIZE);
    short ack_chksum = checksum(ack_pkt);
    memcpy(ack_pkt->data + CHECKSUM_OFFSET, &ack_chksum, CHECKSUM_SIZE);
    Receiver_ToLowerLayer(ack_pkt);
}

/* event handler, called when a packet is passed from the lower layer at the 
   receiver */
void Receiver_FromLowerLayer(struct packet *pkt)
{
    /* if checksum does not match, discard it */
    short chksum_expected = checksum(pkt);
    short chksum = CHECKSUM(pkt->data);
    if (chksum != chksum_expected) 
        return;
    
    int seq_no = SEQ_NO(pkt->data);
    if (seq_no > seq_expected) {
        if (seq_no < seq_expected + WINDOW_SIZE) {
            buffer[seq_no % WINDOW_SIZE].valid = true;
            memcpy(&buffer[seq_no % WINDOW_SIZE].pkt, pkt, sizeof(struct packet));
        } else {
            /* if sequence number is not expected, discard it */
            return;
        }
    } else if (seq_no == seq_expected) {
        buffer[seq_no % WINDOW_SIZE].valid = true;
        memcpy(&buffer[seq_no % WINDOW_SIZE].pkt, pkt, sizeof(struct packet));

        int ack_no = Receiver_ShrinkWindow(seq_no);
        Receiver_SendAck(ack_no);
        seq_expected = ack_no + 1;
    } else {
        /* receive an old packets, resend ack */
        Receiver_SendAck(seq_no);
    }
}
