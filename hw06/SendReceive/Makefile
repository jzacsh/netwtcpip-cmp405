OUTD   :=  bin
SRCD   :=  src
SRCS   :=  $(wildcard $(SRCD)/*.java)
KLSS   :=  $(patsubst $(SRCD)/%.java, %.java, $(SRCS))
JVOBJ  :=  $(patsubst %.java, $(OUTD)/%.class, $(KLSS))
MAINS  :=  $(patsubst %.java, %, $(KLSS))


all: $(JVOBJ)

$(MAINS): $(JVOBJ)
	java -cp $(OUTD) $@

$(OUTD)/%.class: $(SRCD)/%.java
	javac $< -d $(OUTD)/

clean:
	$(RM) $(OUTD)/*.class

.PHONY: clean all
