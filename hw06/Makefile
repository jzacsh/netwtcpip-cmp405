OUTD   :=  bin
SRCD   :=  src
SRCS   :=  $(wildcard $(SRCD)/*.java)
KLSS   :=  $(patsubst $(SRCD)/%.java, %.java, $(SRCS))
JVOBJ  :=  $(patsubst %.java, $(OUTD)/%.class, $(KLSS))

all: $(JVOBJ)

$(OUTD)/%.class: $(SRCD)/%.java
	javac -cp $(SRCD) $< -d $(OUTD)/

clean:
	$(RM) $(OUTD)/*.class

.PHONY: clean all
