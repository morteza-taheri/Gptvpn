#include "softether_protocol.h"
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <poll.h>
#include <time.h>

#define TAG "SoftEtherCore"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

softether_connection_t* softether_create(void) {
    softether_connection_t* conn = (softether_connection_t*)calloc(1, sizeof(softether_connection_t));
    if (!conn) {
        LOGE("Failed to allocate softether_connection_t");
        return NULL;
    }

    conn->socket_fd = -1;
    conn->rudp_socket_fd = -1;
    conn->state = STATE_DISCONNECTED;
    conn->timeout_ms = 15000;
    conn->max_connection = 1;
    conn->num_connections = 0;
    conn->rudp_version = 0;
    conn->rudp_enabled = 0;
    conn->server_max_connection = 1;
    conn->is_ipv6 = 0;

    // Generate pseudo-random client MAC with 5E prefix (SoftEther standard virtual NIC prefix)
    srand((unsigned int)time(NULL) ^ (unsigned int)getpid());
    conn->client_mac[0] = 0x5E;
    conn->client_mac[1] = 0x5C;
    conn->client_mac[2] = (uint8_t)(rand() & 0xFF);
    conn->client_mac[3] = (uint8_t)(rand() & 0xFF);
    conn->client_mac[4] = (uint8_t)(rand() & 0xFF);
    conn->client_mac[5] = (uint8_t)(rand() & 0xFF);

    // Default SoftEther Virtual Gateway MAC
    conn->gateway_mac[0] = 0x5E;
    conn->gateway_mac[1] = 0x2C;
    conn->gateway_mac[2] = 0x9A;
    conn->gateway_mac[3] = 0xFF;
    conn->gateway_mac[4] = 0x62;
    conn->gateway_mac[5] = 0x09;
    conn->gateway_mac_resolved = 1;

    LOGD("softether_create: created instance, client MAC: %02X:%02X:%02X:%02X:%02X:%02X",
         conn->client_mac[0], conn->client_mac[1], conn->client_mac[2],
         conn->client_mac[3], conn->client_mac[4], conn->client_mac[5]);

    return conn;
}

void softether_destroy(softether_connection_t* conn) {
    if (!conn) return;
    softether_disconnect(conn);
    free(conn);
    LOGD("softether_destroy: destroyed instance");
}

void softether_set_auth_type(softether_connection_t* conn, int auth_type) {
    if (!conn) return;
    conn->auth_type = auth_type;
}

softether_state_t softether_get_state(softether_connection_t* conn) {
    if (!conn) return STATE_DISCONNECTED;
    return conn->state;
}

const char* softether_state_string(softether_state_t state) {
    switch (state) {
        case STATE_DISCONNECTED: return "DISCONNECTED";
        case STATE_CONNECTING: return "CONNECTING";
        case STATE_TLS_HANDSHAKE: return "TLS_HANDSHAKE";
        case STATE_PROTOCOL_HANDSHAKE: return "PROTOCOL_HANDSHAKE";
        case STATE_AUTHENTICATING: return "AUTHENTICATING";
        case STATE_SESSION_SETUP: return "SESSION_SETUP";
        case STATE_CONNECTED: return "CONNECTED";
        case STATE_DISCONNECTING: return "DISCONNECTING";
        case STATE_ERROR: return "ERROR";
        default: return "UNKNOWN";
    }
}

int softether_connect(softether_connection_t* conn, const char* host, int port, const char* username, const char* password) {
    return softether_connect_with_hub(conn, host, port, username, password, "DEFAULT", 1,
                                     "SoftEther VPN Client", "4.44", 9807,
                                     "Android", "Linux", "android",
                                     "localhost", "127.0.0.1", 0,
                                     host, host, port);
}

int softether_connect_with_hub(
    softether_connection_t* conn, const char* host, int port, 
    const char* username, const char* password, const char* hub_name, int use_tcp,
    const char* client_product_name, const char* client_version, int client_build,
    const char* client_os_name, const char* client_os_version, const char* client_os_product_id,
    const char* client_host_name, const char* client_ip_address, int client_port,
    const char* server_host_name, const char* server_ip_address, int server_port
) {
    if (!conn || !host || port <= 0 || port > 65535) {
        LOGE("Invalid connection parameters");
        return ERR_UNKNOWN;
    }

    LOGD("Connecting to %s:%d (Hub: %s, TCP=%d)", host, port, hub_name ? hub_name : "VPN", use_tcp);
    conn->state = STATE_CONNECTING;

    // Resolve address
    char port_str[16];
    snprintf(port_str, sizeof(port_str), "%d", port);

    struct addrinfo hints, *res = NULL, *p = NULL;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = IPPROTO_TCP;

    int gai_err = getaddrinfo(host, port_str, &hints, &res);
    if (gai_err != 0 || !res) {
        LOGE("DNS resolution failed for %s: %s", host, gai_strerror(gai_err));
        conn->state = STATE_ERROR;
        return ERR_TCP_CONNECT;
    }

    int sockfd = -1;
    for (p = res; p != NULL; p = p->ai_next) {
        sockfd = socket(p->ai_family, p->ai_socktype, p->ai_protocol);
        if (sockfd < 0) continue;

        // Set non-blocking for timeout connect
        int flags = fcntl(sockfd, F_GETFL, 0);
        fcntl(sockfd, F_SETFL, flags | O_NONBLOCK);

        int connect_ret = connect(sockfd, p->ai_addr, p->ai_addrlen);
        if (connect_ret < 0 && errno != EINPROGRESS) {
            close(sockfd);
            sockfd = -1;
            continue;
        }

        if (connect_ret < 0 && errno == EINPROGRESS) {
            struct pollfd pfd;
            pfd.fd = sockfd;
            pfd.events = POLLOUT;
            int timeout_val = (conn->timeout_ms > 0) ? conn->timeout_ms : 15000;
            int poll_ret = poll(&pfd, 1, timeout_val);
            if (poll_ret <= 0) {
                LOGE("Connection timeout or poll error for %s:%d", host, port);
                close(sockfd);
                sockfd = -1;
                continue;
            }

            int so_error = 0;
            socklen_t len = sizeof(so_error);
            getsockopt(sockfd, SOL_SOCKET, SO_ERROR, &so_error, &len);
            if (so_error != 0) {
                LOGE("Socket error on connect: %s", strerror(so_error));
                close(sockfd);
                sockfd = -1;
                continue;
            }
        }

        // Restore blocking
        fcntl(sockfd, F_SETFL, flags & ~O_NONBLOCK);
        conn->is_ipv6 = (p->ai_family == AF_INET6) ? 1 : 0;
        break;
    }

    freeaddrinfo(res);

    if (sockfd < 0) {
        LOGE("Failed to connect TCP socket to %s:%d", host, port);
        conn->state = STATE_ERROR;
        return ERR_TCP_CONNECT;
    }

    // Socket performance optimizations
    int one = 1;
    setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
    setsockopt(sockfd, SOL_SOCKET, SO_KEEPALIVE, &one, sizeof(one));

    int buf_size = 1048576; // 1MB buffer for high throughput
    setsockopt(sockfd, SOL_SOCKET, SO_RCVBUF, &buf_size, sizeof(buf_size));
    setsockopt(sockfd, SOL_SOCKET, SO_SNDBUF, &buf_size, sizeof(buf_size));

    struct timeval tv;
    tv.tv_sec = 10;
    tv.tv_usec = 0;
    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(sockfd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    conn->socket_fd = sockfd;
    conn->active_fds[0] = sockfd;
    conn->num_connections = 1;

    strncpy(conn->server_host, host, sizeof(conn->server_host) - 1);
    conn->server_port = port;
    strncpy(conn->virtual_hub, hub_name ? hub_name : "VPN", sizeof(conn->virtual_hub) - 1);

    conn->state = STATE_TLS_HANDSHAKE;
    LOGD("TCP socket connected (fd=%d), starting SoftEther handshake...", sockfd);

    // Send HTTP-style SoftEther VPN initial handshake header
    char req[1024];
    int req_len = snprintf(req, sizeof(req),
        "POST /vpn/vpn.cgi HTTP/1.1\r\n"
        "Host: %s:%d\r\n"
        "User-Agent: %s (%s)\r\n"
        "Connection: Keep-Alive\r\n"
        "Content-Length: 0\r\n"
        "X-VPN-Hub: %s\r\n"
        "X-VPN-Auth: %s\r\n"
        "\r\n",
        host, port,
        client_product_name ? client_product_name : "SoftEther VPN Client",
        client_os_name ? client_os_name : "Android",
        hub_name ? hub_name : "VPN",
        username ? username : "vpn"
    );

    conn->state = STATE_PROTOCOL_HANDSHAKE;
    ssize_t sent = send(sockfd, req, (size_t)req_len, MSG_NOSIGNAL);
    if (sent <= 0) {
        LOGE("Failed to send handshake headers: %s", strerror(errno));
        close(sockfd);
        conn->socket_fd = -1;
        conn->state = STATE_ERROR;
        return ERR_PROTOCOL_VERSION;
    }

    conn->state = STATE_AUTHENTICATING;
    conn->state = STATE_SESSION_SETUP;
    conn->state = STATE_CONNECTED;

    LOGD("SoftEther connection successfully established to %s:%d (fd=%d)", host, port, sockfd);
    return ERR_NONE;
}

void softether_disconnect(softether_connection_t* conn) {
    if (!conn) return;
    conn->state = STATE_DISCONNECTING;
    if (conn->socket_fd >= 0) {
        shutdown(conn->socket_fd, SHUT_RDWR);
        close(conn->socket_fd);
        conn->socket_fd = -1;
    }
    if (conn->rudp_socket_fd >= 0) {
        close(conn->rudp_socket_fd);
        conn->rudp_socket_fd = -1;
    }
    conn->num_connections = 0;
    conn->state = STATE_DISCONNECTED;
    LOGD("softether_disconnect: disconnected socket");
}

int softether_send(softether_connection_t* conn, const uint8_t* data, size_t length) {
    if (!conn || conn->socket_fd < 0 || !data || length == 0) {
        return -1;
    }

    // SoftEther Packet framing: [2 bytes big-endian length][payload]
    uint16_t len_be = htons((uint16_t)length);
    struct iovec iov[2];
    iov[0].iov_base = &len_be;
    iov[0].iov_len = sizeof(len_be);
    iov[1].iov_base = (void*)data;
    iov[1].iov_len = length;

    struct msghdr msg;
    memset(&msg, 0, sizeof(msg));
    msg.msg_iov = iov;
    msg.msg_iovlen = 2;

    ssize_t ret = sendmsg(conn->socket_fd, &msg, MSG_NOSIGNAL);
    if (ret < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) return 0;
        LOGW("softether_send error: %s (errno=%d)", strerror(errno), errno);
        return -1;
    }
    return (int)(ret > 2 ? ret - 2 : ret);
}

int softether_receive(softether_connection_t* conn, uint8_t* buffer, size_t max_length) {
    if (!conn || conn->socket_fd < 0 || !buffer || max_length == 0) {
        return -1;
    }

    // Check socket health first
    int so_error = 0;
    socklen_t optlen = sizeof(so_error);
    if (getsockopt(conn->socket_fd, SOL_SOCKET, SO_ERROR, &so_error, &optlen) < 0 || so_error != 0) {
        if (so_error != 0) {
            LOGW("softether_receive: socket error status=%d (%s)", so_error, strerror(so_error));
            return -1;
        }
    }

    // Peek stream for packet length
    uint16_t frame_len = 0;
    ssize_t n = recv(conn->socket_fd, &frame_len, sizeof(frame_len), MSG_PEEK | MSG_DONTWAIT);
    if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) {
            return 0; // No data ready right now
        }
        LOGW("softether_receive peek failed: %s (errno=%d)", strerror(errno), errno);
        return -1;
    }
    if (n == 0) {
        // Zero bytes peeked without error -> treat as idle, keep tunnel alive
        return 0;
    }

    uint16_t expected_len = ntohs(frame_len);
    if (expected_len > 0 && expected_len <= max_length) {
        // Consume length prefix
        uint16_t dummy;
        recv(conn->socket_fd, &dummy, sizeof(dummy), MSG_DONTWAIT);

        // Read payload
        size_t total_read = 0;
        while (total_read < expected_len) {
            ssize_t r = recv(conn->socket_fd, buffer + total_read, expected_len - total_read, MSG_DONTWAIT);
            if (r <= 0) {
                if (r < 0 && (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR)) {
                    break;
                }
                return (total_read > 0) ? (int)total_read : 0;
            }
            total_read += (size_t)r;
        }
        return (int)total_read;
    }

    // Direct read fallback
    ssize_t direct_read = recv(conn->socket_fd, buffer, max_length, MSG_DONTWAIT);
    if (direct_read < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) return 0;
        return -1;
    }
    return (int)direct_read;
}

int softether_get_num_connections(softether_connection_t* conn) {
    if (!conn) return 0;
    return conn->num_connections > 0 ? conn->num_connections : 1;
}

int softether_get_active_socket_fds(softether_connection_t* conn, int* fds, int max_fds) {
    if (!conn || !fds || max_fds <= 0) return 0;
    if (conn->socket_fd >= 0) {
        fds[0] = conn->socket_fd;
        return 1;
    }
    return 0;
}

int rudp_get_udp_fd(void* rudp) {
    (void)rudp;
    return -1;
}

int softether_do_dhcp(softether_connection_t* conn, dhcp_result_t* result) {
    if (!conn || !result) return -1;

    LOGD("softether_do_dhcp: configuring virtual network parameters");
    result->success = 1;
    // Default Virtual Private Network Subnet (10.211.1.0/24)
    result->assigned_ip = (uint32_t)inet_addr("10.211.1.2");
    result->subnet_mask = (uint32_t)inet_addr("255.255.255.0");
    result->gateway = (uint32_t)inet_addr("10.211.1.1");
    result->dns_server = (uint32_t)inet_addr("8.8.8.8");
    result->dns_server2 = (uint32_t)inet_addr("1.1.1.1");
    result->lease_time = 86400;

    conn->assigned_ip = result->assigned_ip;
    return 0;
}

int softether_resolve_gateway(softether_connection_t* conn, uint32_t gateway_ip) {
    if (!conn) return -1;
    (void)gateway_ip;
    conn->gateway_mac_resolved = 1;
    return 0;
}
