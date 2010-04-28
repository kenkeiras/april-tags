include ../common.mk

CFLAGS = $(CFLAGS_STD) $(CFLAGS_COMMON) $(CFLAGS_DGC) $(CFLAGS_GLIB) $(CFLAGS_LCM) $(CFLAGS_LCMTYPES) -D_GNU_SOURCE -Iliburg

LDFLAGS = $(LDFLAGS_DGC) $(LDFLAGS_COMMON) $(LDFLAGS_STD) $(LDFLAGS_GLIB) $(LDFLAGS_LCM) $(LDFLAGS_LCMTYPES)

HOKUYO_OBJS = hokuyo.o 

HOKUYO=$(BIN_PATH)/hokuyo

all: liburg/liburg.a $(HOKUYO)


liburg/liburg.a:
	cd liburg && make

$(HOKUYO): $(HOKUYO_OBJS)
	$(CC) -o $@ $(HOKUYO_OBJS) liburg/liburg.a $(LDFLAGS)

clean:
	rm -f *.o *.a *~ $(HOKUYO)
	make -C liburg clean
