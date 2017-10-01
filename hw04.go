package main

import (
	"fmt"

	"github.com/jzacsh/netwtcpip-cmp405/parseip4"
)

var partTwoHosts = []parseip4.Addr{
	{IP: parseip4.NewAddr(9, 201, 195, 84), Mask: parseip4.NewAddr(255, 255, 240, 0)},
	{IP: parseip4.NewAddr(128, 10, 189, 215), Mask: parseip4.NewAddr(255, 255, 248, 0)},
	{IP: parseip4.NewAddr(135, 21, 243, 82), Mask: parseip4.NewAddr(255, 255, 224, 0)},
	{IP: parseip4.NewAddr(75, 149, 205, 61), Mask: parseip4.NewAddr(255, 255, 192, 0)},
	{IP: parseip4.NewAddr(7, 105, 198, 111), Mask: parseip4.NewAddr(255, 255, 252, 0)},

	// TODO(zacsh) remove this sample from the last slide
	{IP: parseip4.NewAddr(128, 10, 211, 78), Mask: parseip4.NewAddr(255, 255, 240, 0)},
}

type subnetRequisites struct {
	MaxSubnets  uint
	SubnetIndex parseip4.Octets
	HostIndex   parseip4.Octets
}

type OptimalSubnet struct {
	MinSubnetBits     uint
	MaxHostsPerSubnet parseip4.Octets
	SubnetMask        parseip4.OctsList
}

var partOneGivens = []subnetRequisites{
	{55, 51, 121},
	{55, 42, 867},
	{121, 115, 246},
	{121, 97, 443},
	{26, 19, 237},
	{26, 25, 1397},
	{261, 227, 86},
	{261, 259, 49},
	{529, 519, 33},
	{529, 510, 59},
}

func (s *subnetRequisites) String() string {
	return fmt.Sprintf(
		"max subnets: %d, subnet index: %d, host index: %d",
		s.MaxSubnets, s.SubnetIndex, s.HostIndex)
}

func (s *subnetRequisites) FindSolution() OptimalSubnet {
	return OptimalSubnet{} // TODO implement
}

func main() {
	fmt.Printf("part 1: analyzing %d hosts ...\n", len(partOneGivens))
	for _, req := range partOneGivens {
		sol := req.FindSolution()
		fmt.Printf(
			"  given: %s\n\tmin # of subnet bits: %d\n\tmax # hosts per subnet: %d\n\tsubnetmask: %d\n",
			req.String(),
			sol.MinSubnetBits,
			sol.MaxHostsPerSubnet,
			sol.SubnetMask)
	}

	fmt.Printf("\n\npart 2: analyzing %d hosts ...\n", len(partTwoHosts))
	for _, addr := range partTwoHosts {
		classMask, _, klass := parseip4.Classful(addr.IP)

		fmt.Printf(
			"  network: %v (class %s masked)\n\t%v\n\tnetwork id:\t%d\n\t subnet id:\t%d\n\t   host id:\t%d\n",
			(addr.IP.Pack() & classMask.Pack()).List(), klass,
			addr.String(),
			addr.NetworkIndex(),
			addr.SubnetIndex(),
			addr.HostIndex())
	}
}
