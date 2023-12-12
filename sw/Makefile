BIN=spmvtest
SRCDIR=.
SRC := $(SRCDIR)/main.c

CFLAGS :=
CFLAGS += -fopenmp

all:
	gcc -o ${BIN} ${SRC}

inclusive:
	gcc -DMSHR_INCLUSIVE -o ${BIN} ${SRC}

clean:
	rm ${BIN}
