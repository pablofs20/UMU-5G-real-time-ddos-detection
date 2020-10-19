#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pcap.h>

#include "sniffer.h"

struct config {
  char interface[40];
  char filter[40];
  char broker[40];
  char topic[40];
};

int getIndex (char* str, char ch) {
  int index;
  char* ch_c;

  ch_c = strchr(str, ch);
  return (int)(ch_c - str);
}

int main(int argc, char *argv[]) {

	static const char filename[] = "config/sniffer.conf";
  FILE *file = fopen ( filename, "r" );

  struct config configuration;

  if ( file != NULL ) {
    char line [ 128 ];
    while ( fgets ( line, sizeof line, file ) != NULL ) {

			char *pos;
			if ((pos=strchr(line, '\n')) != NULL)
    	*pos = '\0';

      int index = getIndex(line, '=');

      char* valor = &line[index + 1];

      line[index] = 0;

      char* clave = line;

      if (!strcmp(clave, "INTERFACE"))
          strcpy(configuration.interface, valor);

      if (!strcmp(clave, "FILTER"))
          strcpy(configuration.filter, valor);

      if (!strcmp(clave, "BROKER"))
          strcpy(configuration.broker, valor);

      if (!strcmp(clave, "TOPIC"))
          strcpy(configuration.topic, valor);
    }
    fclose ( file );
  }
  else {
   perror ( filename );
  }

	const char* device = configuration.interface;
	const char* filter = configuration.filter;
	const char* broker = configuration.broker;
	const char* topic  = configuration.topic;

	sniffer_init(device, filter, broker, topic);

	return 0;

}
