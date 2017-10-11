OUTD   :=  bin
SRCD   :=  src


$(OUTD)/%.class: $(SRCD)/%.java
	javac $< -d $(OUTD)/

clean:
	$(RM) $(OUTD)/*.class

.PHONY: clean
