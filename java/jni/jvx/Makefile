COMMON_INCLUDES=-I../../../src/
LDFLAGS_COMMON=../../../lib/libcommon.a

# gtk
CFLAGS_GTK   :=`pkg-config --cflags gtk+-2.0`
LDFLAGS_GTK  :=`pkg-config --libs gtk+-2.0 gthread-2.0`

#############################################################
# Linux
ifeq "$(shell uname)" "Linux"

CC = gcc
# XXX Need add optimization back in!!
CCFLAGS = -std=gnu99 -fno-stack-protector -fPIC  -D_REENTRANT -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -Wno-unused-parameter -Wno-format-zero-length -Wall -Werror=implicit-function-declaration -g

JNI_INCLUDES = -I/usr/lib/jvm/java-6-sun/include/ -I/usr/lib/jvm/java-6-sun/include/linux -I/usr/lib/jvm/java-6-openjdk/include/ -I/usr/lib/jvm/java-7-openjdk-amd64/include/

LD = ld
LDFLAGS = -shared -lX11 -lGL -lGLEW
SHARED_JNI_EXT = so
STATIC_EXT = a
LIBVX_OBJS = glcontext-x11.o

endif

#############################################################
# MacOS X
ifeq "$(shell uname)" "Darwin"

CC = gcc
CCFLAGS = -std=gnu99 -O2 -D_REENTRANT -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -Wno-unused-parameter -Wno-format-zero-length
JNI_INCLUDES = -I/System/Library/Frameworks/JavaVM.framework/Headers/ -I/usr/X11/include/

LD = gcc
LDFLAGS = -dynamiclib -framework OpenGL -framework JavaVM -L/usr/X11/lib -lX11 -lGL -lGLEW
SHARED_JNI_EXT = jnilib
STATIC_EXT = a #JS not sure
LIBVX_OBJS = glcontext-x11.o

endif

#############################################################
# Core rules

LIB_JNI=libjvx.$(SHARED_JNI_EXT)
DESTLIB_JNI=../../../lib/$(LIB_JNI)

LIB=libvx.$(STATIC_EXT)
DESTLIB=../../../lib/$(LIB)


all:  $(DESTLIB_JNI) $(DESTLIB)

$(DESTLIB) : $(LIB)
	cp $(LIB) $(DESTLIB)

$(DESTLIB_JNI) : $(LIB_JNI)
	cp $(LIB_JNI) $(DESTLIB_JNI)


LIBVX_USER_OBJS = vx_world.o vx_layer.o vx_util.o vx_program.o vx_matrix_stack.o vx_points.o

LIBVX_OBJS += vx_renderer.o vx_local_renderer.o glcontext.o  lphash.o vx_code_input_stream.o vx_code_output_stream.o vx_resc_mgr.o vx_resc.o vhash.o lihash.o larray.o varray.o sort_util.o $(LIBVX_USER_OBJS)

LIB_JNI_OBJS += vx_jni.o


$(LIB_JNI) : april_vx_VxLocalRenderer.h $(LIB_JNI_OBJS) $(LIB)
	$(LD)  $(LDFLAGS_COMMON) $(LIB_JNI_OBJS)  $(LDFLAGS) -L./ -lvx  -o $@

$(LIB):  $(LIBVX_OBJS)
	ar rc $@ $(LIBVX_OBJS)




#############################################################
%.o: %.c
	$(CC) $(CCFLAGS) -c $(JNI_INCLUDES) $(COMMON_INCLUDES) $<

april_vx_VxLocalRenderer.h: ../../src/april/vx/VxLocalRenderer.java
	@echo "NOTICE: Rebuilding JNI headers. Ensure java file has been recently built."
	javah -classpath ../../april.jar  april.vx.VxLocalRenderer

clean:
	rm -rf $(LIB_JNI_OBJS) $(LIBVX_OBJS) *~ april_vx_VxLocalServer.h $(LIB) $(DESTLIB) $(LIB_JNI) $(DESTLIB_JNI)


lphash_test:  varray.o lphash.o lphash_test.o
	gcc -o $@ $^

maptest:  vhash.o varray.o maptest.o
	gcc -o $@ $^

SORT_OBJS=sort_test.o sort_util.o varray.o
sort_test: $(SORT_OBJS)
	$(CC) -o $@ $(SORT_OBJS)