OUTD   :=  bin
SRCD   :=  src
SRCS   :=  $(wildcard $(SRCD)/*.java)
KLSS   :=  $(patsubst $(SRCD)/%.java, %.java, $(SRCS))
JVOBJ  :=  $(patsubst %.java, $(OUTD)/%.class, $(KLSS))

hw9: clean all
	@echo 'Now just run:'
	@echo '  java -cp $(OUTD) Chatterbox -username YOUR_USER_NAME'

all: $(JVOBJ)

$(OUTD)/%.class: $(SRCD)/%.java
	javac -Xlint:unchecked -cp $(SRCD) $< -d $(OUTD)/

lint:
	./importscrub.sh $(SRCS)

clean:
	$(RM) $(OUTD)/*.class

.PHONY: clean all
