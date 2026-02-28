PYTHON ?= python3
L ?= 2018:585
SOURCE_MODE ?= hybrid
DATA_DIR ?= data
JAR ?= target/sfsreader-1.0-SNAPSHOT.jar

.PHONY: help lag

help:
	@echo "Targets:"
	@echo "  make lag L=2018:585 [SOURCE_MODE=hybrid] [DATA_DIR=data] [JAR=target/sfsreader-1.0-SNAPSHOT.jar]"

lag:
	$(PYTHON) tools/fetch_process_law.py "$(L)" --source-mode "$(SOURCE_MODE)" --data-dir "$(DATA_DIR)" --jar "$(JAR)"
