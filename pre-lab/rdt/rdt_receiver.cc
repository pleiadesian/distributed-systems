/*
 * FILE: rdt_receiver.cc
 * DESCRIPTION: Reliable data transfer receiver.
 * NOTE: In this implementation, the packet format is laid out as 
 *       the following:
 *       
 *       |<-        1 byte        ->|
 *       |<-      packet info     ->|
 *       |<-1 bit->|<-    7 bit   ->|<-    4 byte    ->|<-             the rest            ->|
 *       |<- EOM ->|<-payload size->|<-  seq/ack no. ->|<-             payload             ->|
 */


#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "rdt_struct.h"
#include "rdt_receiver.h"

#define MAX_MESSAGE_SIZE    10000
#define INFO_OFFSET         0
#define INFO_SIZE           1
#define SEQNUM_OFFSET       INFO_OFFSET + INFO_SIZE
#define SEQNUM_SIZE         sizeof(int)
#define PAYLOAD_OFFSET      SEQNUM_OFFSET + SEQNUM_SIZE

#define END_OF_MESSAGE(data)    ((data[INFO_OFFSET] & 0X80) >> 7)
#define SEQ_NO(data)                                                           \
  {                                                                            \
    (data[SEQNUM_OFFSET] << 24 | data[SEQNUM_OFFSET + 1] << 16 |               \
     data[SEQNUM_OFFSET + 2] << 8 | data[SEQNUM_OFFSET + 3])                   \
  }
#define PAYLOAD_SIZE(data)      (data[INFO_OFFSET] & 0X7F)

static char *curr_msg_data;             // current message to be constructed
static int curr_msg_size = 0;           // current message size
static int seq_expected = 0;            // next seq expected inbound

/* receiver initialization, called once at the very beginning */
void Receiver_Init()
{
    fprintf(stdout, "At %.2fs: receiver initializing ...\n", GetSimulationTime());
    curr_msg_data = (char *)malloc(MAX_MESSAGE_SIZE);
    ASSERT(curr_msg_data != NULL);
}

/* receiver finalization, called once at the very end.
   you may find that you don't need it, in which case you can leave it blank.
   in certain cases, you might want to use this opportunity to release some 
   memory you allocated in Receiver_init(). */
void Receiver_Final()
{
    fprintf(stdout, "At %.2fs: receiver finalizing ...\n", GetSimulationTime());
    free(curr_msg_data);
}

/* event handler, called when a packet is passed from the lower layer at the 
   receiver */
void Receiver_FromLowerLayer(struct packet *pkt)
{
    /* if sequence number is not expected, discard it */
    int seq_no = SEQ_NO(pkt->data);
    if (seq_no != seq_expected) 
        return;
    
    /* concatenate the new data into current constructing message */
    ASSERT(PAYLOAD_SIZE(pkt->data) > 0 && PAYLOAD_SIZE(pkt->data) <= RDT_PKTSIZE - SEQNUM_SIZE - INFO_SIZE);
    memcpy(curr_msg_data + curr_msg_size, pkt->data + PAYLOAD_OFFSET, PAYLOAD_SIZE(pkt->data));
    curr_msg_size += PAYLOAD_SIZE(pkt->data);

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

    /* acknowledge the sender */
    struct packet *ack_pkt = (struct packet *)malloc(sizeof(struct packet));
    memset(ack_pkt->data, '\0', RDT_PKTSIZE);
    memcpy(ack_pkt->data + SEQNUM_OFFSET, &seq_no, SEQNUM_SIZE);
    Receiver_ToLowerLayer(ack_pkt);
}
