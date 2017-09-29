package main

import (
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"os"
	"strings"

	"./blob"
	"./inet"
	"github.com/jzacsh/hexint"
)

type FrameHead interface {
	RawHeader() []byte
}

type stringer interface {
	String() string
}

type FrameBody interface {
	// This should be ignored if HasHeader() is false.
	ModuleFrame

	FrameHead

	stringer
}

type ModuleFrame interface {
	HasHeader() bool

	// ParseHead takes a frame of bytes and returns two subsets or two nils and a
	// parsing error. The two subsets of `frame` which a module should return are:
	// - that beginning subset which the module identified as its own header
	// - the remainder subset which the module identified as its own header
	ParseHead() (FrameHead, FrameBody, error)
}

func decodeHexStream(input io.Reader) ([]byte, error) {
	chars, e := ioutil.ReadAll(os.Stdin)
	if e != nil {
		return nil, fmt.Errorf("reading from stdin: %s", e)
	}
	if len(chars) < 0 {
		return nil, fmt.Errorf("require non-zero fake datagram, got on chars\n")
	}

	onLeftHalf := true
	bytes := make([]byte, 0, 256)
	for _, char := range chars {
		ch := strings.TrimSpace(string(char))
		if len(ch) == 0 {
			continue
		}
		if len(ch) > 1 {
			return nil, fmt.Errorf(
				"got non ASCII char (some UTF-8 rune?); at col %d: '%s'", len(bytes), ch)
		}

		hex := byte(ch[0])
		integer, e := hexint.DecodeInt(hex)
		if e != nil {
			return nil, fmt.Errorf("input at [octet #%d] '%c': %v", len(bytes), hex, e)
		}

		if onLeftHalf {
			bytes = append(bytes, byte(integer<<4))
		} else {
			bytes[len(bytes)-1] = bytes[len(bytes)-1] & byte(integer<<4)
		}
		onLeftHalf = !onLeftHalf
	}

	return bytes, nil
}

func main() {
	bytes, e := decodeHexStream(os.Stdin)
	if e != nil {
		log.Fatalf("decoding hex from stdin: %v", e)
	}
	log.Printf("\nprocessing %d bytes of input...\n", len(bytes))

	ethFrame := inet.EthFrame{Blob: blob.ByteBlob{Data: bytes}}
	_, ipFrame, e := ethFrame.ParseHead()
	if e != nil {
		log.Fatalf("parsing ethernet frame: %s", e)
	}
	log.Printf("ethernet frame:\n%v\n", ethFrame.String())

	_, payload, e := ipFrame.ParseHead()
	if e != nil {
		log.Fatalf("parsing ip frame: %s", e)
	}
	log.Printf("ip frame:\n%v\n", ipFrame.String())
	log.Printf("above-IP level payload (eg: application-bound):\n%s\n", payload)
}
