package main

import (
	"fmt"
)

type Octets uint32 // all 4 octets, in their respective bit locations

type OctsList []int // len always 4

func (o OctsList) Pack() Octets {
	return (Octets(o[0]) << 24) +
		(Octets(o[1]) << 16) +
		(Octets(o[2]) << 8) +
		(Octets(o[3]))
}

func (ino Octets) List() OctsList {
	return OctsList{
		int((0xFF000000 & ino) >> 24),
		int((0x00FF0000 & ino) >> 16),
		int((0x0000FF00 & ino) >> 8),
		int(0x000000FF & ino),
	}
}

type Addr struct {
	ip   OctsList
	mask OctsList
}

func (a *Addr) String() string {
	return fmt.Sprintf(
		"%d.%d.%d.%d/%d.%d.%d.%d",
		a.ip[0], a.ip[1], a.ip[2], a.ip[3],
		a.mask[0], a.mask[1], a.mask[2], a.mask[3])
}

func (a *Addr) Classful() (OctsList, string) {
	topOctet := a.ip[0]
	switch {
	case topOctet < 128:
		return OctsList{255, 0, 0, 0}, "A"
	case topOctet >= 128 && topOctet < 192:
		return OctsList{255, 255, 0, 0}, "B"
	case topOctet >= 192 && topOctet < 224:
		return OctsList{255, 255, 255, 0}, "C"
	default:
		panic("expected only classes A,B, or C")
	}
}

var hosts = []Addr{
	{ip: OctsList{9, 201, 195, 84}, mask: OctsList{255, 255, 240, 0}},
	{ip: OctsList{128, 10, 189, 215}, mask: OctsList{255, 255, 248, 0}},
	{ip: OctsList{135, 21, 243, 82}, mask: OctsList{255, 255, 224, 0}},
	{ip: OctsList{75, 149, 205, 61}, mask: OctsList{255, 255, 192, 0}},
	{ip: OctsList{7, 105, 198, 111}, mask: OctsList{255, 255, 252, 0}},
}

func main() {
	fmt.Printf("analyzing %d hosts ...\n", len(hosts))
	for _, addr := range hosts {
		ipPck := addr.ip.Pack()
		maskPck := addr.mask.Pack()
		hostMaskPck := ^addr.mask.Pack()

		classMask, klass := addr.Classful()
		classMaskPck := classMask.Pack()

		subnetMaskPck := (^classMaskPck) & maskPck
		fmt.Printf(
			"  network: %v (class %s masked)\n\t%v\n\tsubnet : %d <- %v\n\thost   : %d <- %v\n\n",
			(ipPck & classMaskPck).List(), klass,
			addr.String(),
			ipPck&subnetMaskPck, (ipPck & subnetMaskPck).List(),
			ipPck&hostMaskPck, (ipPck & hostMaskPck).List())
	}
}
