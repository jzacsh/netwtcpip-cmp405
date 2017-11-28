// Package parseip4 provides some common functionality for inspecting an IP
// address, per IP v4, including addresses using CIDR.
package parseip4

import (
	"fmt"
)

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

const (
	shiftOctetA = 32 - 8   // octet A in IP address A.B.C.D
	shiftOctetB = 32 - 8*2 // octet B in IP address A.B.C.D
	shiftOctetC = 32 - 8*3 // octet C in IP address A.B.C.D
)

// Pack produces a single integer with tthe values of OctsList in the
// appropriate bit location.
func (o OctsList) Pack() Octets {
	return (Octets(o[0]) << shiftOctetA) +
		(Octets(o[1]) << shiftOctetB) +
		(Octets(o[2]) << shiftOctetC) +
		(Octets(o[3]))
}

// OctsList unpacks a Octets value into its component 8-bit valued integers.
func (ino Octets) List() OctsList {
	return OctsList{
		uint8((0xFF000000 & ino) >> shiftOctetA),
		uint8((0x00FF0000 & ino) >> shiftOctetB),
		uint8((0x0000FF00 & ino) >> shiftOctetC),
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
func Classful(ip OctsList) (OctsList, uint, string) {
	topOctet := ip[0]
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

// CountOnes counts the on-bits of an 32-bit octets integer.
func CountOnes(o Octets) uint {
	var n uint = 0
	for i := n; i < 32; i++ {
		n += 1 & (uint(o) >> i)
	}
	return n
}

// CountBitSize counts how much of 32 bit address space is utilized `o`.
func CountBitSize(o Octets) uint {
	var i uint = 0
	for ; i < 32; i++ {
		if ((1 << 31) & (o << i)) != 0 {
			break
		}
	}
	return 32 - i
}

// NetworkIndex determines what the integer index of an address's network number
// would be, if it were considered as one of a continuous stream of potential
// networks within the IP address space.
func (a *Addr) NetworkIndex() Octets {
	classMask, cidrOffset, _ := Classful(a.IP)
	return (a.IP.Pack() & classMask.Pack()) >> (32 - cidrOffset)
}

// SubnetIndex determines what the integer index of an address's subnet number
// would be, if it were considered as one of a continuous stream of potential
// networks within the IP address space.
func (a *Addr) SubnetIndex() Octets {
	classMask, cidrOffset, _ := Classful(a.IP)
	classMaskPck := classMask.Pack()

	uniqSubnetBits := (^classMaskPck) & a.Mask.Pack()
	middleSubnetBits := uniqSubnetBits & a.IP.Pack()
	totalNetBitsCount := cidrOffset + CountOnes(uniqSubnetBits)
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
