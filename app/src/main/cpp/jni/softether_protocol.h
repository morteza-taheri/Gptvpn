#ifndef SOFTETHER_PROTOCOL_H
#define SOFTETHER_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MAX_SE_CONNECTIONS 32

typedef enum {
    STATE_DISCONNECTED = 0,
    STATE_CONNECTING = 1,
    STATE_TLS_HANDSHAKE = 2,
    STATE_PROTOCOL_HANDSHAKE = 3,
    STATE_AUTHENTICATING = 4,
    STATE_SESSION_SETUP = 5,
    STATE_CONNECTED = 6,
    STATE_DISCONNECTING = 7,
    STATE_ERROR = 8
} softether_state_t;

enum {
    ERR_NONE = 0,
    ERR_TCP_CONNECT = 1,
    ERR_TLS_HANDSHAKE = 2,
    ERR_PROTOCOL_VERSION = 3,
    ERR_AUTHENTICATION = 4,
    ERR_SESSION = 5,
    ERR_DATA_TRANSMISSION = 6,
    ERR_TIMEOUT = 7,
    ERR_UNKNOWN = 99
};

typedef struct {
    int success;
    uint32_t assigned_ip;
    uint32_t subnet_mask;
    uint32_t gateway;
    uint32_t dns_server;
    uint32_t dns_server2;
    uint32_t lease_time;
} dhcp_result_t;

typedef struct softether_connection {
    int socket_fd;
    int rudp_socket_fd;
    softether_state_t state;
    int timeout_ms;
    int auth_type;
    int max_connection;
    int num_connections;
    int active_fds[MAX_SE_CONNECTIONS + 1];
    int rudp_version;
    int rudp_enabled;
    void* rudp;
    int server_max_connection;
    int is_ipv6;
    uint32_t assigned_ip;
    uint8_t client_mac[6];
    uint8_t gateway_mac[6];
    int gateway_mac_resolved;
    char server_host[256];
    int server_port;
    char virtual_hub[64];
} softether_connection_t;

// Core functions
softether_connection_t* softether_create(void);
void softether_destroy(softether_connection_t* conn);
int softether_connect(softether_connection_t* conn, const char* host, int port, const char* username, const char* password);
int softether_connect_with_hub(
    softether_connection_t* conn, const char* host, int port, 
    const char* username, const char* password, const char* hub_name, int use_tcp,
    const char* client_product_name, const char* client_version, int client_build,
    const char* client_os_name, const char* client_os_version, const char* client_os_product_id,
    const char* client_host_name, const char* client_ip_address, int client_port,
    const char* server_host_name, const char* server_ip_address, int server_port
);
void softether_set_auth_type(softether_connection_t* conn, int auth_type);
void softether_disconnect(softether_connection_t* conn);
softether_state_t softether_get_state(softether_connection_t* conn);
const char* softether_state_string(softether_state_t state);

int softether_send(softether_connection_t* conn, const uint8_t* data, size_t length);
int softether_receive(softether_connection_t* conn, uint8_t* buffer, size_t max_length);

int softether_get_num_connections(softether_connection_t* conn);
int softether_get_active_socket_fds(softether_connection_t* conn, int* fds, int max_fds);
int rudp_get_udp_fd(void* rudp);

int softether_do_dhcp(softether_connection_t* conn, dhcp_result_t* result);
int softether_resolve_gateway(softether_connection_t* conn, uint32_t gateway_ip);

#ifdef __cplusplus
}
#endif

#endif // SOFTETHER_PROTOCOL_H
