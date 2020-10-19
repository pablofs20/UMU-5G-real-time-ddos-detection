// FICHERO SNIFFER.C

#include <stdio.h>
#include <stdlib.h>
#include <inttypes.h>
#include <pcap.h>
#include <netinet/in.h>
#include <netinet/if_ether.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <netinet/tcp.h>
#include <string.h>
#include <time.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include "sniffer.h"
#include "gtp.h"
#include <stdio.h>
#include <signal.h>
#include <string.h>
#include <linux/types.h>
#include <librdkafka/rdkafka.h>

rd_kafka_t * rk;
rd_kafka_conf_t * conf;

const char * brokers;
const char * topic;

FILE * fptr;
struct timeval tv;
unsigned long inicio;
int paquetes_procesados;

struct vxlanhdr {
    __be32 flags;
    __be32 vni;
};

const struct ether_header * eth_e0;
const struct ether_header * eth_e1;
const struct ip * ip4_e0;
const struct ip * ip4_e1;
const struct ip * ip4_e2;
const struct udphdr * udp_e0;
const struct udphdr * udp_e1;
const struct vxlanhdr * vxlan;
const struct gtp1_header * gtp;
const struct tcphdr * tcp;
uint8_t * payload;

void init_producer(const char * broker,
    const char * ttopic) {

    char errstr[512];

    brokers = broker;
    topic = ttopic;

    conf = rd_kafka_conf_new();

    if (rd_kafka_conf_set(conf, "bootstrap.servers", brokers,
            errstr, sizeof(errstr)) != RD_KAFKA_CONF_OK) {
        fprintf(stderr, "%s\n", errstr);
    }

    rk = rd_kafka_new(RD_KAFKA_PRODUCER, conf, errstr, sizeof(errstr));
    if (!rk) {
        fprintf(stderr,
            "%% Failed to create new producer: %s\n", errstr);
    }
}

void end_producer() {
    fprintf(stderr, "%% Flushing final messages..\n");
    rd_kafka_flush(rk, 10 * 1000);

    if (rd_kafka_outq_len(rk) > 0)
        fprintf(stderr, "%% %d message(s) were not delivered\n",
            rd_kafka_outq_len(rk));

    rd_kafka_destroy(rk);
}

int publish_message(char * clave, char * mensaje) {
    size_t lenMensaje = strlen(mensaje);
    size_t lenClave = strlen(clave);
    rd_kafka_resp_err_t err;

    if (mensaje[lenMensaje - 1] == '\n')
        mensaje[--lenMensaje] = '\0';

    if (clave[lenClave - 1] == '\n')
        clave[--lenClave] = '\0';

    if (lenMensaje == 0 || lenClave == 0) {
        rd_kafka_poll(rk, 0);
    }

    retry:
        err = rd_kafka_producev(
            rk,
            RD_KAFKA_V_TOPIC(topic),
            RD_KAFKA_V_MSGFLAGS(RD_KAFKA_MSG_F_COPY),
            RD_KAFKA_V_VALUE(mensaje, lenMensaje),
            RD_KAFKA_V_KEY(clave, lenClave),
            RD_KAFKA_V_OPAQUE(NULL),
            RD_KAFKA_V_END);

    if (err) {
        fprintf(stderr,
            "%% Failed to produce to topic %s: %s\n",
            topic, rd_kafka_err2str(err));

        if (err == RD_KAFKA_RESP_ERR__QUEUE_FULL) {
            rd_kafka_poll(rk, 1000);
            goto retry;
        }
    } else {
        fprintf(stderr, "%% Enqueued message (%zd bytes) "
            "for topic %s\n",
            lenMensaje, topic);
    }

    rd_kafka_poll(rk, 0);

}

int ip_to_int(char * ip) {
    const char s[2] = ".";
    char * token;

    char * copy = malloc(strlen(ip) + 1);
    strcpy(copy, ip);

    int suma = 0;

    token = strtok(copy, s);

    while (token != NULL) {
        suma += atoi(token);
        token = strtok(NULL, s);
    }

    free(copy);
    return suma;

}

void packet_handler(uint8_t * args,
    const struct pcap_pkthdr * header,
        const uint8_t * packet) {

    printf("packet_handler -> new packet captured\n");

    //
    //
    // ENCAPSULATION LAYER: 0
    //
    //

    eth_e0 = (struct ether_header * )(packet);

    char mac_origen_e0[100];
    sprintf(mac_origen_e0, "%x:%x:%x:%x:%x:%x", (unsigned int) eth_e0 -> ether_shost[0],
        (unsigned int) eth_e0 -> ether_shost[1],
        (unsigned int) eth_e0 -> ether_shost[2],
        (unsigned int) eth_e0 -> ether_shost[3],
        (unsigned int) eth_e0 -> ether_shost[4],
        (unsigned int) eth_e0 -> ether_shost[5]);

    char mac_destino_e0[100];
    sprintf(mac_destino_e0, "%x:%x:%x:%x:%x:%x", (unsigned int) eth_e0 -> ether_dhost[0],
        (unsigned int) eth_e0 -> ether_dhost[1],
        (unsigned int) eth_e0 -> ether_dhost[2],
        (unsigned int) eth_e0 -> ether_dhost[3],
        (unsigned int) eth_e0 -> ether_dhost[4],
        (unsigned int) eth_e0 -> ether_dhost[5]);

    ip4_e0 = (struct ip * )(packet + SIZE_ETHERNET);

    int total_paquete = ip4_e0 -> ip_len + SIZE_ETHERNET;

    char ipSrc_e0[30];
    strcpy(ipSrc_e0, (char * ) inet_ntoa((struct in_addr) ip4_e0 -> ip_src));

    char ipDst_e0[30];
    strcpy(ipDst_e0, (char * ) inet_ntoa((struct in_addr) ip4_e0 -> ip_dst));

    udp_e0 = (struct udphdr * )(packet + SIZE_ETHERNET + SIZE_IP);

    int flowId_e0 = (ip_to_int(ipSrc_e0) + ip_to_int(ipDst_e0)) * (udp_e0 -> uh_sport + udp_e0 -> uh_dport);

    char * l7proto_e0;

    if (ntohs(udp_e0 -> uh_dport) == 4789 || ntohs(udp_e0 -> uh_sport) == 4789) {
        l7proto_e0 = "vxlan";
    } else {
        l7proto_e0 = "unknown";
    }

    //
    //
    // ENCAPSULATION LAYER: 1
    //
    //

    vxlan = (struct vxlanhdr * )(packet + SIZE_ETHERNET + SIZE_IP + SIZE_UDP);

    int vni = be32toh(vxlan -> vni << 8);

    eth_e1 = (struct ether_header * )(packet + SIZE_ETHERNET + SIZE_IP + SIZE_UDP + SIZE_VXLAN);

    char mac_origen_e1[100];
    sprintf(mac_origen_e1, "%x:%x:%x:%x:%x:%x", (unsigned int) eth_e1 -> ether_shost[0],
        (unsigned int) eth_e1 -> ether_shost[1],
        (unsigned int) eth_e1 -> ether_shost[2],
        (unsigned int) eth_e1 -> ether_shost[3],
        (unsigned int) eth_e1 -> ether_shost[4],
        (unsigned int) eth_e1 -> ether_shost[5]);

    char mac_destino_e1[100];
    sprintf(mac_destino_e1, "%x:%x:%x:%x:%x:%x", (unsigned int) eth_e1 -> ether_dhost[0],
        (unsigned int) eth_e1 -> ether_dhost[1],
        (unsigned int) eth_e1 -> ether_dhost[2],
        (unsigned int) eth_e1 -> ether_dhost[3],
        (unsigned int) eth_e1 -> ether_dhost[4],
        (unsigned int) eth_e1 -> ether_dhost[5]);

    ip4_e1 = (struct ip * )(packet + SIZE_ETHERNET + SIZE_IP + SIZE_UDP + SIZE_VXLAN + SIZE_ETHERNET);

    char ipSrc_e1[30];
    strcpy(ipSrc_e1, (char * ) inet_ntoa((struct in_addr) ip4_e1 -> ip_src));

    char ipDst_e1[30];
    strcpy(ipDst_e1, (char * ) inet_ntoa((struct in_addr) ip4_e1 -> ip_dst));

    udp_e1 = (struct udphdr * )(packet + SIZE_ETHERNET + SIZE_IP + SIZE_UDP + SIZE_VXLAN + SIZE_ETHERNET +
        SIZE_IP);

    int flowId_e1 = (ip_to_int(ipSrc_e1) + ip_to_int(ipDst_e1)) * (udp_e1 -> uh_sport + udp_e1 -> uh_dport);

    char * l7proto_e1;

    if (ntohs(udp_e1 -> uh_dport) == 2152 || ntohs(udp_e1 -> uh_sport) == 2152) {
        l7proto_e1 = "gtp-u";
    } else if (ntohs(udp_e1 -> uh_dport) == 2123 || ntohs(udp_e1 -> uh_sport) == 2123) {
        l7proto_e1 = "gtp-c";
    } else {
        l7proto_e1 = "unknown";
    }

    //
    //
    // ENCAPSULATION LAYER: 2
    //
    //

    gtp = gtp = (struct gtp1_header * )(packet + SIZE_ETHERNET + SIZE_IP + SIZE_UDP + SIZE_VXLAN + SIZE_ETHERNET +
        SIZE_IP + SIZE_UDP);

    unsigned long int tid = be32toh(gtp -> tid);

    ip4_e2 = (struct ip * )(packet + SIZE_ETHERNET + SIZE_IP + SIZE_UDP + SIZE_VXLAN + SIZE_ETHERNET +
        SIZE_IP + SIZE_UDP + SIZE_GTP);

    char ipSrc_e2[30];
    strcpy(ipSrc_e2, (char * ) inet_ntoa((struct in_addr) ip4_e2 -> ip_src));

    char ipDst_e2[30];
    strcpy(ipDst_e2, (char * ) inet_ntoa((struct in_addr) ip4_e2 -> ip_dst));

    if (ip4_e2 -> ip_p != 6)
        return;

    tcp = (struct tcphdr * )(packet + SIZE_ETHERNET + SIZE_IP + SIZE_UDP + SIZE_VXLAN + SIZE_ETHERNET +
        SIZE_IP + SIZE_UDP + SIZE_GTP + SIZE_IP);

    int len_tcp = tcp -> th_off * 4;

    printf("la longitud de la cabecera tcp es %d\n", len_tcp);

    int flowId_e2 = (ip_to_int(ipSrc_e2) + ip_to_int(ipDst_e2)) * (tcp -> source + tcp -> dest);

    char * l7proto_e2;

    if (ntohs(tcp -> dest) == 80) {
        l7proto_e2 = "http";
    } else if (ntohs(tcp -> dest) == 443) {
        l7proto_e2 = "https";
    } else {
        l7proto_e2 = "unknown";
    }

    gettimeofday( & tv, NULL);
    unsigned long time_in_micros = 1000000 * tv.tv_sec + tv.tv_usec;

    char json[10000];

    sprintf(json, "{\n"
        "   \"data\": [\n"
        "      {\n"
        "         \"flowId\": \"%d\",\n"
        "         \"encapsulationLayer\": \"0\",\n"
        "         \"macSrc\": \"%s\",\n"
        "         \"macDst\": \"%s\",\n"
        "         \"srcIP\": \"%s\",\n"
        "         \"dstIP\": \"%s\",\n"
        "         \"l4Proto\": \"%d\",\n"
        "         \"srcPort\": \"%d\",\n"
        "         \"dstPort\": \"%d\",\n"
        "         \"l7Proto\": \"%s\" },\n"
        "      {\n"
        "         \"flowId\": \"%d\",\n"
        "         \"encapsulationLayer\": \"1\",\n"
        "         \"encapsulationID1\": \"%d\",\n"
        "         \"encapsulationType1\": \"vxlan\",\n"
        "         \"macSrc\": \"%s\",\n"
        "         \"macDst\": \"%s\",\n"
        "         \"l3Proto\": \"%d\",\n"
        "         \"srcIP\": \"%s\",\n"
        "         \"dstIP\": \"%s\",\n"
        "         \"outSrcIP\": \"%s\",\n"
        "         \"outDstIP\": \"%s\",\n"
        "         \"l4Proto\": \"%d\",\n"
        "         \"srcPort\": \"%d\",\n"
        "         \"dstPort\": \"%d\",\n"
        "         \"l7Proto\": \"%s\" },\n"
        "      {\n"
        "         \"flowId\": \"%d\",\n"
        "         \"encapsulationLayer\": \"2\",\n"
        "         \"encapsulationID2\": \"%ld\",\n"
        "         \"encapsulationType2\": \"gtp\",\n"
        "         \"srcIP\": \"%s\",\n"
        "         \"dstIP\": \"%s\",\n"
        "         \"outSrcIP\": \"%s\",\n"
        "         \"outDstIP\": \"%s\",\n"
        "         \"l4Proto\": \"%d\",\n"
        "         \"srcPort\": \"%d\",\n"
        "         \"dstPort\": \"%d\",\n"
        "         \"l7Proto\": \"%s\",\n"
        "         \"timestamp\": \"%ld.%.2ld\",\n"
        "         \"ipTotalLength\": \"%d\",\n"
        "         \"ipTTL\": \"%d\",\n"
        "         \"tcpWindowSize\": \"%d\",\n"
        "         \"tcpFin:\": \"%d\",\n"
        "         \"tcpSyn:\": \"%d\",\n"
        "         \"tcpRst:\": \"%d\",\n"
        "         \"tcpPsh:\": \"%d\",\n"
        "         \"tcpAck:\": \"%d\",\n"
        "         \"tcpUrg:\": \"%d\" }\n"
        "   ]\n"
        "}\n\n"

        , flowId_e0
        , mac_origen_e0
        , mac_destino_e0
        , ipSrc_e0
        , ipDst_e0
        , ip4_e0 -> ip_p
        , ntohs(udp_e0 -> uh_sport)
        , ntohs(udp_e0 -> uh_dport)
        , l7proto_e0
        , flowId_e1
        , vni
        , mac_origen_e1
        , mac_destino_e1
        , ntohs(eth_e1 -> ether_type)
        , ipSrc_e1, ipDst_e1, ipSrc_e0
        , ipDst_e0, ip4_e1 -> ip_p
        , ntohs(udp_e1 -> uh_sport)
        , ntohs(udp_e1 -> uh_dport)
        , l7proto_e1
        , flowId_e2
        , tid
        , ipSrc_e2
        , ipDst_e2
        , ipSrc_e0
        , ipDst_e0
        , ip4_e2 -> ip_p
        , ntohs(tcp -> source)
        , ntohs(tcp -> dest)
        , l7proto_e2
        , time_in_micros / 1000000
        , time_in_micros % 1000000
        , ntohs(ip4_e2 -> ip_len)
        , ip4_e2 -> ip_ttl
        , ntohs(tcp -> window)
        , tcp -> fin
        , tcp -> syn
        , tcp -> rst
        , tcp -> psh
        , tcp -> ack
        , tcp -> urg);

    char flowId_e2_str[100];
    sprintf(flowId_e2_str, "%d", flowId_e2);

    paquetes_procesados++;

    int bytes_procesados = paquetes_procesados * 140;

    publish_message(flowId_e2_str, json);

    printf("packet_handler -> packet handled\n\n");

    gettimeofday( & tv, NULL);
    unsigned long fin = tv.tv_sec * 1000LL + tv.tv_usec / 1000;
    unsigned long transcurrido = fin - inicio;

    char entrada[200];
    sprintf(entrada, "%ld %d\n", transcurrido, bytes_procesados);

    fputs(entrada, fptr);

    fflush(fptr);

    return;

}

int sniffer_init(const char * device,
    const char * filter_exp,
        const char * broker,
            const char * topic) {

    init_producer(broker, topic);

    paquetes_procesados = 0;

    fptr = fopen("sniffer.dat", "w");

    gettimeofday( & tv, NULL);
    inicio = tv.tv_sec * 1000LL + tv.tv_usec / 1000;
    printf("inicio : %ld\n", inicio);

    char error_buffer[PCAP_ERRBUF_SIZE];
    pcap_t * handle;
    int promiscuous = 0;
    int snapshot_len = SIZE_ETHERNET + SIZE_IP + SIZE_UDP + SIZE_VXLAN + SIZE_ETHERNET + SIZE_IP +
        SIZE_UDP + SIZE_GTP + SIZE_IP + SIZE_TCP;
    int timeout = 100;

    struct bpf_program fp;
    uint32_t mask;
    uint32_t net;

    if (pcap_lookupnet(device, & net, & mask, error_buffer) == -1) {
        fprintf(stderr, "Can't get netmask for device %s\n", device);
        net = 0;
        mask = 0;
    }

    handle = pcap_open_live(device,
        snapshot_len,
        promiscuous,
        timeout,
        error_buffer);
    if (handle == NULL) {
        fprintf(stderr, "Could not open file, %s\n", error_buffer);
        return 2;
    }

    if (pcap_compile(handle, & fp, filter_exp, 0, net) == -1) {
        fprintf(stderr, "Couldn't parse filter %s: %s\n", filter_exp, pcap_geterr(handle));
        return 2;
    }

    if (pcap_setfilter(handle, & fp) == -1) {
        fprintf(stderr, "Couldn't install filter %s: %s\n", filter_exp, pcap_geterr(handle));
        return 2;
    }

    pcap_loop(handle, 0, packet_handler, NULL);

    printf("Sniffing in %s, looking for \"%s\" packets\n\n", device, filter_exp);

    pcap_close(handle);

    end_producer();

    return 0;

}
