// Package ip provides that highlevel behavior described in RFC 791 which is
// referred to as "ip module" responsibility, and which has been in the
// scope of my undergrad networking course (and my own research).
package ip

// IPPayload is a FrameBody
type IPPayload struct {
	Blob []byte
}

func (ipp *IPPayload) RawHeader() []byte { return ipp.Blob }

func (ipp *IPPayload) HasHeader() bool { return len(ipp.Blob) > 0 }

func (ipp *IPPayload) String() string {
	return "dump of IP frame fields not yet implemented" // TODO
}

// ParseHead takes a frame blob of bytes and returns two subsets or two nils and
// a parsing error. The two subsets of `blob` which a module should return are:
// - that beginning subset which the module identified as its own header
// - the remainder subset which the module identified as its own header
func (ipp *IPPayload) ParseHead() (IPPayload, PseudoAppModule, error) {
	panic("ParseHead not yet implemented")
	return *ipp, PseudoAppModule{Unclaimed: ipp.Blob[len(ipp.Blob):]}, nil
}
