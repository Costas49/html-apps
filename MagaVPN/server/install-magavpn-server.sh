#!/usr/bin/env bash
set -euo pipefail

# MagaVPN hardened WireGuard server bootstrap for Ubuntu 24.04 / Debian 12+
WG_IF="wg0"
WG_PORT="${WG_PORT:-51820}"
WG_NET4="10.66.66.0/24"
WG_SERVER4="10.66.66.1/24"
WG_CLIENT4="10.66.66.2/32"
WG_NET6="fd42:42:42::/64"
WG_SERVER6="fd42:42:42::1/64"
WG_CLIENT6="fd42:42:42::2/128"
CLIENT_DNS4="${CLIENT_DNS4:-1.1.1.1}"
CLIENT_DNS4_ALT="${CLIENT_DNS4_ALT:-1.0.0.1}"

if [[ "${EUID}" -ne 0 ]]; then echo "Run as root."; exit 1; fi
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y wireguard nftables qrencode curl ca-certificates unattended-upgrades

WAN_IF="$(ip -4 route show default | awk '/default/ {print $5; exit}')"
PUBLIC4="$(curl -4fsS --max-time 10 https://api.ipify.org || true)"
PUBLIC6="$(ip -6 addr show scope global | awk '/inet6/ {print $2}' | cut -d/ -f1 | head -n1 || true)"
[[ -n "${WAN_IF}" && -n "${PUBLIC4}" ]] || { echo "Public network detection failed."; exit 1; }

install -d -m 700 /etc/wireguard /root/magavpn
umask 077
SERVER_PRIVATE="$(wg genkey)"
SERVER_PUBLIC="$(printf '%s' "${SERVER_PRIVATE}" | wg pubkey)"
CLIENT_PRIVATE="$(wg genkey)"
CLIENT_PUBLIC="$(printf '%s' "${CLIENT_PRIVATE}" | wg pubkey)"
PRESHARED="$(wg genpsk)"

cat >/etc/sysctl.d/99-magavpn.conf <<EOF
net.ipv4.ip_forward=1
net.ipv6.conf.all.forwarding=1
net.ipv4.conf.all.src_valid_mark=1
EOF
sysctl --system >/dev/null

WG_ADDRESS="${WG_SERVER4}"
CLIENT_ADDRESS="${WG_CLIENT4}"
ALLOWED_ROUTES="0.0.0.0/0"
PEER_ALLOWED="${WG_CLIENT4}"
if [[ -n "${PUBLIC6}" ]]; then
  WG_ADDRESS="${WG_ADDRESS}, ${WG_SERVER6}"
  CLIENT_ADDRESS="${CLIENT_ADDRESS}, ${WG_CLIENT6}"
  ALLOWED_ROUTES="${ALLOWED_ROUTES}, ::/0"
  PEER_ALLOWED="${PEER_ALLOWED}, ${WG_CLIENT6}"
fi

cat >/etc/wireguard/${WG_IF}.conf <<EOF
[Interface]
Address = ${WG_ADDRESS}
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_PRIVATE}
SaveConfig = false

[Peer]
PublicKey = ${CLIENT_PUBLIC}
PresharedKey = ${PRESHARED}
AllowedIPs = ${PEER_ALLOWED}
EOF
chmod 600 /etc/wireguard/${WG_IF}.conf

cat >/etc/nftables.conf <<EOF
#!/usr/sbin/nft -f
flush ruleset
table inet filter {
  chain input {
    type filter hook input priority 0; policy drop;
    iifname "lo" accept
    ct state established,related accept
    ip protocol icmp accept
    ip6 nexthdr ipv6-icmp accept
    tcp dport 22 ct state new accept
    udp dport ${WG_PORT} ct state new accept
  }
  chain forward {
    type filter hook forward priority 0; policy drop;
    ct state established,related accept
    iifname "${WG_IF}" oifname "${WAN_IF}" accept
    iifname "${WAN_IF}" oifname "${WG_IF}" ct state established,related accept
  }
  chain output { type filter hook output priority 0; policy accept; }
}
table ip nat {
  chain postrouting {
    type nat hook postrouting priority 100; policy accept;
    ip saddr ${WG_NET4} oifname "${WAN_IF}" masquerade
  }
}
EOF

if [[ -n "${PUBLIC6}" ]]; then
cat >>/etc/nftables.conf <<EOF
table ip6 nat {
  chain postrouting {
    type nat hook postrouting priority 100; policy accept;
    ip6 saddr ${WG_NET6} oifname "${WAN_IF}" masquerade
  }
}
EOF
fi

nft -c -f /etc/nftables.conf
systemctl enable --now nftables
nft -f /etc/nftables.conf

cat >/root/magavpn/android.conf <<EOF
[Interface]
PrivateKey = ${CLIENT_PRIVATE}
Address = ${CLIENT_ADDRESS}
DNS = ${CLIENT_DNS4}, ${CLIENT_DNS4_ALT}
MTU = 1280

[Peer]
PublicKey = ${SERVER_PUBLIC}
PresharedKey = ${PRESHARED}
Endpoint = ${PUBLIC4}:${WG_PORT}
AllowedIPs = ${ALLOWED_ROUTES}
PersistentKeepalive = 25
EOF
chmod 600 /root/magavpn/android.conf

systemctl enable --now wg-quick@${WG_IF}
systemctl restart wg-quick@${WG_IF}

cat >/etc/ssh/sshd_config.d/99-magavpn-hardening.conf <<EOF
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
X11Forwarding no
EOF
sshd -t
systemctl reload ssh || systemctl reload sshd
dpkg-reconfigure -f noninteractive unattended-upgrades || true

echo "=== MAGAVPN READY ==="
echo "Public IPv4: ${PUBLIC4}"
echo "WireGuard UDP: ${WG_PORT}"
echo "Client config: /root/magavpn/android.conf"
qrencode -t ansiutf8 </root/magavpn/android.conf
