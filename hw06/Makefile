OUTD   :=  bin
SRCD   :=  src
SRCS   :=  $(wildcard $(SRCD)/*.java)
KLSS   :=  $(patsubst $(SRCD)/%.java, %.java, $(SRCS))
JVOBJ  :=  $(patsubst %.java, $(OUTD)/%.class, $(KLSS))

Chatterbox: $(OUTD)/Chatterbox.class

all: $(JVOBJ)

$(OUTD)/%.class: $(SRCD)/%.java
	javac -Xlint:unchecked -cp $(SRCD) $< -d $(OUTD)/

lint:
	./importscrub.sh $(SRCS)

clean:
	$(RM) $(OUTD)/*.class

.PHONY: clean all
