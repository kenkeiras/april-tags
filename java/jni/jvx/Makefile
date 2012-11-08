COMMON_INCLUDES=-I../../../src/
LDFLAGS_COMMON=../../../lib/libcommon.a

#############################################################
# Linux
ifeq "$(shell uname)" "Linux"

CC = gcc
CCFLAGS = -std=gnu99 -fno-stack-protector -fPIC -O2 -D_REENTRANT -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -Wno-unused-parameter -Wno-format-zero-length

JNI_INCLUDES = -I/usr/lib/jvm/java-6-sun/include/ -I/usr/lib/jvm/java-6-sun/include/linux -I/usr/lib/jvm/java-6-openjdk/include/ -I/usr/lib/jvm/java-7-openjdk-amd64/include/

LD = ld
LDFLAGS = -shared -lX11 -lGL
SHARED_EXT = so

LIBJGL_OBJS = glcontext-x11.o

endif

#############################################################
# MacOS X
ifeq "$(shell uname)" "Darwin"

CC = gcc
CCFLAGS = -std=gnu99 -O2 -D_REENTRANT -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -Wno-unused-parameter -Wno-format-zero-length
JNI_INCLUDES = -I/System/Library/Frameworks/JavaVM.framework/Headers/ -I/usr/X11/include/

LD = gcc
LDFLAGS = -dynamiclib -framework OpenGL -framework JavaVM -L/usr/X11/lib -lX11 -lGL
SHARED_EXT = jnilib

LIBJGL_OBJS = glcontext-x11.o

endif

#############################################################
# Core rules

LIB=libjvx.$(SHARED_EXT)
DESTLIB=../../../lib/$(LIB)

all:  $(DESTLIB)


$(DESTLIB) : $(LIB)
	cp $(LIB) $(DESTLIB)

LIBJGL_OBJS += vx_jni.o vx.o glcontext.o  lphash.o vx_code_input_stream.o #parray.o
$(LIB): april_vx_VxLocalServer.h $(LIBJGL_OBJS)
	$(LD) $(LDFLAGS) $(LDFLAGS_COMMON) $(LIBJGL_OBJS) -o $@

#############################################################
%.o: %.c
	$(CC) $(CCFLAGS) -c $(JNI_INCLUDES) $(COMMON_INCLUDES) $<

april_vx_VxLocalServer.h: ../../src/april/vx/VxLocalServer.java
	@echo "NOTICE: Rebuilding JNI headers. Ensure java file has been recently built."
	javah -classpath ../../april.jar  april.vx.VxLocalServer

clean:
	rm -rf $(LIBJGL_OBJS) *~ april_vx_VxLocalServer.h $(LIB) $(DESTLIB)
