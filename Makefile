OUT_EXT  :=  bin

CC        =  clang
LDLIBS    =
LDFLAGS   =  -g
INCLUDES  =
CFLAGS    =  -std=c99 -O0 -g -Wall $(INCLUDES)

sampleethframe:
	@git show 341a73f6d601:lecture03_20170911.adoc  | sed -n 93,100p

.PHONY: clean sampleethframe

clean:
	$(RM) -rf *.$(OUT_EXT)
