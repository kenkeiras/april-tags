
CFLAGS_STD   := $(CFLAGS_ARG) -std=gnu99 -g \
	-D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -D_LARGEFILE64_SOURCE \
	-Wall -Wno-unused-parameter -Wno-format-zero-length

CFLAGS:=$(CFLAGS_STD)

lib_obj:= delay.o \
	getTicks.o \
	scip_handler.o \
	serial_ctrl.o \
	serial_ctrl_lin.o \
	serial_utils.o \
	urg_ctrl.o \
	urg_errno.o

.phony: all
all:  liburg.a

liburg.a: $(lib_obj)
	ar rc $@ $(lib_obj)


%.o: %.c %.h
	@echo "    [$@]"
	$(CC) $(CFLAGS) -c $< 

%.o: %.c
	@echo "    [$@]"
	$(CC) $(CFLAGS) -c $< 


clean:
	rm -f $(lib_obj) liburg.a
