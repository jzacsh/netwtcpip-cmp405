OUT_EXT  :=  bin

CC        =  clang
LDLIBS    =
LDFLAGS   =  -g
INCLUDES  =
CFLAGS    =  -std=c99 -O0 -g -Wall $(INCLUDES)

test: memcheck

memcheck:
	$(MAKE) sampleethframe | valgrind --error-exitcode=1 --leak-check=yes --suppressions=valgrind.supp ./framespect.bin

sampleethframe:
	@git show 341a73f6d601:lecture03_20170911.adoc  | sed -n 93,100p

.PHONY: clean sampleethframe memcheck test

clean:
	$(RM) -rf *.$(OUT_EXT)
