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

func mustIntFromHex(hex byte) int {
	resp, e := getIntFromHex(hex)
	if e != nil {
		panic(e)
	}
	return resp
}

// Given a hex char, returns its corresponding integer: a value in [0,15]
func getIntFromHex(hex byte) (int, error) {
	// Char-to-rune table:
	//
	// | char    code point
	// +-------------------
	// | [0,9] = [48,57]
	// | [A,F] = [65,70]
	// | [a,f] = [97,102]

	runePoint := rune(hex)
	val := getValidHex(runePoint)
	if val == -1 {
		return -1, fmt.Errorf("invalid hex, got: '%c'", runePoint)
	}
	return val, nil // TODO(zacsh) finish converting to int [0,15]
}

func isValidHex(rn rune) bool { return getValidHex(rn) != -1 }

func getValidHex(rn rune) int {
	switch {
	case (rn >= 48 && rn <= 57): /*[0,9]*/
		fmt.Fprintf(os.Stderr, "think '%c' is [0,9]\n", rn) // TODO(zacsh) debugging; remove
	case (rn >= 65 && rn <= 70): /*[A,F]*/
		fmt.Fprintf(os.Stderr, "think '%c' is [A,F]\n", rn) // TODO(zacsh) debugging; remove
	case (rn >= 97 && rn <= 102): /*[a,f]*/
		fmt.Fprintf(os.Stderr, "think '%c' is [a,f]\n", rn) // TODO(zacsh) debugging; remove
	default:
		return -1
	}
	fmt.Fprintf(os.Stderr, "think '%c' IS hex; returning %d\n", rn, int(rn)) // TODO(zacsh) debugging; remove
	return int(rn)
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
