package ip

// PseudoAppModule is a sad FrameBody
type PseudoAppModule struct {
	Unclaimed []byte
}

// Actually returns entire blob, since there's no distinction between "header"
// and "payload", as HasHeader() returning false means this remainder is at the
// end of the line.
func (appMod *PseudoAppModule) RawHeader() []byte {
	return appMod.Unclaimed
}

func (appMod *PseudoAppModule) HasHeader() bool { return false }

func (appMod *PseudoAppModule) String() string {
	return "fictious dump not yet implemented" // TODO
}

func (appMod *PseudoAppModule) ParseHead(frame []byte) (PseudoAppModule, PseudoAppModule, error) {
	panic("ParseHead() called, despite HasHeader() being false")
}
