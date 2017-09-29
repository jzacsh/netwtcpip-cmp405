OUT_EXT  :=  bin

CC        =  clang
LDLIBS    =
LDFLAGS   =  -g
INCLUDES  =
CFLAGS    =  -std=c99 -O0 -g -Wall $(INCLUDES)

default: framespect.bin

all: clean test

test: framespect.bin memcheck

memcheck:
	$(MAKE) sampleethframe | valgrind --track-origins=yes --error-exitcode=1 --leak-check=yes --suppressions=valgrind.supp ./framespect.bin

sampleethframe:
	@git show 8e5e667dde950:testdata_ipframe/ipdatagram_sample04.txt

.PHONY: clean sampleethframe memcheck test all default

clean:
	$(RM) -rf *.$(OUT_EXT)
