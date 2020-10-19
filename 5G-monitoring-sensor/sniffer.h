#ifndef IPV6SNIFFER_H
#define IPV6SNIFFER_H

#include <stdlib.h>
#include <inttypes.h>
#include <pcap.h>

#define SIZE_ETHERNET 14
#define SIZE_IPV6 40
#define SIZE_UDP 8
#define SIZE_MTU_IPV6 1280
#define SIZE_IP 20
#define SIZE_GTP 8
#define SIZE_TCP 20
#define SIZE_VXLAN 8

/*
 * Packet handler for pcap_loop
 * It parses a raw packet into a struct udpIp6_packet
 */
void packet_handler(uint8_t *args,
						const struct pcap_pkthdr *header,
						const uint8_t *packet);

/*
 * Starts the sniffer in a device to capture "filter_exp" packets
 */
int sniffer_init(const char *device, const char *filter_exp, const char *broker, const char *topic);

#endif
