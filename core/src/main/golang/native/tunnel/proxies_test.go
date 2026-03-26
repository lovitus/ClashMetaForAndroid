package tunnel

import (
	"testing"
	"time"

	C "github.com/metacubex/mihomo/constant"
)

func TestPickDelayTestURL(t *testing.T) {
	now := time.Unix(1_700_000_000, 0)

	tests := []struct {
		name      string
		histories map[string]C.ProxyState
		want      string
	}{
		{
			name:      "empty histories fallback to default",
			histories: map[string]C.ProxyState{},
			want:      C.DefaultTestURL,
		},
		{
			name: "pick latest timestamp across urls",
			histories: map[string]C.ProxyState{
				"https://a.example.com": {History: []C.DelayHistory{{Time: now.Add(-time.Minute), Delay: 100}}},
				"https://b.example.com": {History: []C.DelayHistory{{Time: now, Delay: 200}}},
			},
			want: "https://b.example.com",
		},
		{
			name: "no history prefers default url when present",
			histories: map[string]C.ProxyState{
				C.DefaultTestURL:        {},
				"https://a.example.com": {},
			},
			want: C.DefaultTestURL,
		},
		{
			name: "no history without default uses stable lexical fallback",
			histories: map[string]C.ProxyState{
				"https://b.example.com": {},
				"https://a.example.com": {},
			},
			want: "https://a.example.com",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := pickDelayTestURL(tt.histories)
			if got != tt.want {
				t.Fatalf("pickDelayTestURL() = %q, want %q", got, tt.want)
			}
		})
	}
}
