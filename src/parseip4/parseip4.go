// Package parseip4 provides some common functionality for inspecting an IP
// address, per IP v4, including addresses using CIDR.
package parseip4

import "fmt"

// Octets represents all 4 octets of an address, in as single unsigned integer.
// To produce a sensible Octets value, see OctsList.Pack().
type Octets uint32

// OctsList is a (presumed) len()=4 list of 8-bit unsigned integer values, or
// octets.
type OctsList []uint8

// Addr represents an IP address and its mask, which may or may not be a
// default classful addressing mask.
type Addr struct {
	IP   OctsList
	Mask OctsList
}

// NewAddr builds a proper-length OctsList.
func NewAddr(highest, second, third, lowest uint8) OctsList {
	return OctsList{highest, second, third, lowest}
}

// Pack produces a single integer with tthe values of OctsList in the
// appropriate bit location.
func (o OctsList) Pack() Octets {
	return (Octets(o[0]) << 24) +
		(Octets(o[1]) << 16) +
		(Octets(o[2]) << 8) +
		(Octets(o[3]))
}

// OctsList unpacks a Octets value into its component 8-bit valued integers.
func (ino Octets) List() OctsList {
	return OctsList{
		uint8((0xFF000000 & ino) >> 24),
		uint8((0x00FF0000 & ino) >> 16),
		uint8((0x0000FF00 & ino) >> 8),
		uint8(0x000000FF & ino),
	}
}

func (a *Addr) String() string {
	return fmt.Sprintf(
		"%d.%d.%d.%d/%d.%d.%d.%d",
		a.IP[0], a.IP[1], a.IP[2], a.IP[3],
		a.Mask[0], a.Mask[1], a.Mask[2], a.Mask[3])
}

// Classful determines the classful-addressing "class" of an IP address and
// returns three representations of this: a mask, a CIDR-notation offset, and
// the human-readable class label.
func (a *Addr) Classful() (OctsList, uint, string) { // TODO(zacsh) make this a `OctsList` address
	topOctet := a.IP[0]
	switch {
	case topOctet < 128:
		return OctsList{255, 0, 0, 0}, 8 * 1, "A"
	case topOctet >= 128 && topOctet < 192:
		return OctsList{255, 255, 0, 0}, 8 * 2, "B"
	case topOctet >= 192 && topOctet < 224:
		return OctsList{255, 255, 255, 0}, 8 * 3, "C"
	default:
		panic("expected only classes A,B, or C")
	}
}

func countSubnetBits(o Octets) uint {
	var n uint = 0
	for i := n; i < 32; i++ {
		n += 1 & (uint(o) >> i)
	}
	return n
}

// NetworkIndex determines what the integer index of an address's network number
// would be, if it were considered as one of a continuous stream of potential
// networks within the IP address space.
func (a *Addr) NetworkIndex() Octets {
	classMask, cidrOffset, _ := a.Classful()
	return (a.IP.Pack() & classMask.Pack()) >> (32 - cidrOffset)
}

// SubnetIndex determines what the integer index of an address's subnet number
// would be, if it were considered as one of a continuous stream of potential
// networks within the IP address space.
func (a *Addr) SubnetIndex() Octets {
	classMask, cidrOffset, _ := a.Classful()
	classMaskPck := classMask.Pack()

	uniqSubnetBits := (^classMaskPck) & a.Mask.Pack()
	middleSubnetBits := uniqSubnetBits & a.IP.Pack()
	totalNetBitsCount := cidrOffset + countSubnetBits(uniqSubnetBits)
	return middleSubnetBits >> (32 - totalNetBitsCount)
}

// HostIndex determines what the integer index of an address's host number
// would be - after considering any classless subnet apparently being utilized,
// according to the addresses' mask - if it were considered as one of a
// continuous stream of potential networks within the IP address space.
func (a *Addr) HostIndex() Octets {
	maskPck := a.Mask.Pack()
	return (^maskPck) & a.IP.Pack()
}
