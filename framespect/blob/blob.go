// Package blob provides ByteBlob to maintain a cursor across a byte slice and
// some common operations all layers require
package blob

type ByteBlob struct {
	Data   []byte
	cursor int
}

func (bb *ByteBlob) Next(count int) []byte {
	next := bb.Data[bb.cursor : bb.cursor+count]
	bb.cursor += count
	return next
}

func (bb *ByteBlob) Remainder() []byte {
	remaining := len(bb.Data) - bb.cursor
	return bb.Next(remaining)
}

func (bb *ByteBlob) RemainingBlob() ByteBlob {
	return ByteBlob{Data: bb.Remainder()}
}

func (bb *ByteBlob) IsEmpty() bool { return len(bb.Data) > 0 }
