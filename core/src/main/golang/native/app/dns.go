package app

import (
	"strings"

	"cfa/native/tunnel"

	"github.com/metacubex/mihomo/dns"
	"github.com/metacubex/mihomo/log"
)

func NotifyDnsChanged(dnsList string) {
	var addr []string
	if len(dnsList) > 0 {
		addr = strings.Split(dnsList, ",")
	}
	dns.UpdateSystemDNS(addr)
	dns.FlushCacheWithDefaultResolver()
	// Android network switches can leave app-side flows stale even when DNS is unchanged.
	log.Infoln("[APP] network environment changed, close active connections")
	tunnel.CloseAllConnections()
}
