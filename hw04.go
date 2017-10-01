package main

import (
	"fmt"

	"./src/parseip4"
)

var hosts = []parseip4.Addr{
	{IP: parseip4.NewAddr(9, 201, 195, 84), Mask: parseip4.NewAddr(255, 255, 240, 0)},
	{IP: parseip4.NewAddr(128, 10, 189, 215), Mask: parseip4.NewAddr(255, 255, 248, 0)},
	{IP: parseip4.NewAddr(135, 21, 243, 82), Mask: parseip4.NewAddr(255, 255, 224, 0)},
	{IP: parseip4.NewAddr(75, 149, 205, 61), Mask: parseip4.NewAddr(255, 255, 192, 0)},
	{IP: parseip4.NewAddr(7, 105, 198, 111), Mask: parseip4.NewAddr(255, 255, 252, 0)},

	// TODO(zacsh) remove this sample from the last slide
	{IP: parseip4.NewAddr(128, 10, 211, 78), Mask: parseip4.NewAddr(255, 255, 240, 0)},
}

func main() {
	fmt.Printf("analyzing %d hosts ...\n", len(hosts))
	for _, addr := range hosts {
		classMask, _, klass := parseip4.Classful(addr.IP)

		fmt.Printf(
			"  network: %v (class %s masked)\n\t%v\n\tnetwork id:\t%d\n\t subnet id:\t%d\n\t   host id:\t%d\n\n",
			(addr.IP.Pack() & classMask.Pack()).List(), klass,
			addr.String(),
			addr.NetworkIndex(),
			addr.SubnetIndex(),
			addr.HostIndex())
	}
}
