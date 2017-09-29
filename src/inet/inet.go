// Package inet provides that highlevel behavior described in RFC 791 which is
// referred to as "internet module" responsibility, and which has been in the
// scope of my undergrad networking course (and my own research).
package inet

import (
	"fmt"

	"../blob"
	"../ip"
)

// EthFrame is a FrameBody whose ParseHead() returns an EthFrame and
// IPPayload.
type EthFrame struct {
	Blob blob.ByteBlob

	ethDestination []byte
	ethSource      []byte
	ethType        []byte
}

func (ef *EthFrame) RawHeader() []byte { return ef.Blob.Data }

func (ef *EthFrame) HasHeader() bool {
	return ef.Blob.IsEmpty()
}

func (ef *EthFrame) String() string {
	return fmt.Sprintf(
		`  destination hardware addr: 0x% X
  source hardware addr: 0x% X
  ethernet frame type: 0x% X
`, ef.ethDestination, ef.ethSource, ef.ethType)
}

// ParseHead takes a frame blob of bytes and returns two subsets or two nils and
// a parsing error. The two subsets of `frame` which a module should return are:
// - that beginning subset which the module identified as its own header
// - the remainder subset which the module identified as its own header
func (ef *EthFrame) ParseHead() (EthFrame, ip.IPPayload, error) {
	ef.ethDestination = ef.Blob.Next(6)
	ef.ethSource = ef.Blob.Next(6)
	ef.ethType = ef.Blob.Next(2)
	return *ef, ip.IPPayload{Blob: ef.Blob.RemainingBlob()}, nil
}
