inherit region-source
inherit machine
inherit smart-rootfs

DESCRIPTION = "RT-Smart User Applications"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=de10de48642ab74318e893a61105afbb"

APP_NAME = "busybox-1.35.0"
APP_MD5SUM = "585949b1dd4292b604b7d199866e9913"
SRC_URI_CN = "https://mirror.nju.edu.cn/buildroot/busybox/${APP_NAME}.tar.bz2;md5sum=${APP_MD5SUM}"
SRC_URI_GITHUB = "https://www.busybox.net/downloads/${APP_NAME}.tar.bz2;md5sum=${APP_MD5SUM}"

BP = "busybox-1.35.0"

BUSYBOX_LINK ?= "static"

python () {
    toolchain_for_machine(d)
    set_preferred_source(d)
}

busybox_target_libdir() {
    case "${ARCH}" in
        riscv64) printf '%s\n' "risc-v/rv64gc" ;;
        aarch64) printf '%s\n' "aarch64/cortex-a" ;;
        arm) printf '%s\n' "arm/cortex-a" ;;
        *) bbfatal "Unknown ARCH for busybox SDK library: ${ARCH}" ;;
    esac
}

busybox_target_cflags() {
    case "${ARCH}" in
        riscv64) printf '%s\n' "-march=rv64imafdc -mabi=lp64" ;;
        aarch64) printf '%s\n' "-march=armv8-a" ;;
        arm) printf '%s\n' "-march=armv7-a" ;;
        *) bbfatal "Unknown ARCH for busybox CFLAGS: ${ARCH}" ;;
    esac
}

busybox_target_ldflags() {
    sdk_libdir="$(busybox_target_libdir)"

    if [ "${BUSYBOX_LINK}" = "dynamic" ]; then
        printf '%s\n' "-L. -L\${LIB_DIR} -L\$(RT_DIR)/lib/${sdk_libdir} -Wl,--start-group -Wl,-whole-archive -lrtthread -Wl,-no-whole-archive -Wl,--end-group"
    else
        printf '%s\n' "-L."
    fi
}

busybox_sync_makefile_toolchain() {
    makefile="$1"
    sdk_libdir="$(busybox_target_libdir)"
    target_cflags="$(busybox_target_cflags)"
    target_ldflags="$(busybox_target_ldflags)"

    sed -i \
        -e "s|^CROSS_COMPILE=.*|CROSS_COMPILE= ${RTT_CC_PREFIX}|" \
        -e "s|^LIB_DIR=.*|LIB_DIR=\${SDK_DIR}/sdk/lib/${sdk_libdir}|" \
        -e "s|^CFLAGS =.*|CFLAGS = ${target_cflags} -fvisibility=hidden -O3 -I. -I\$(RT_DIR)/include -I\$(RT_DIR)/components/dfs -I\$(RT_DIR)/components/drivers -I\$(RT_DIR)/components/finsh -I\$(RT_DIR)/components/net -I\${INC_DIR}|" \
        -e "s|^LDFLAGS =.*|LDFLAGS = ${target_ldflags}|" \
        "$makefile"
}

do_build_rootfs() {
    bbplain "##############################"
    do_compile
}
do_build_rootfs[depends] = "smart-gcc:do_install_toolchain"

do_compile() {
    bbplain "##############################"
    #bbplain "${PATH}"
    #bbplain "${FILE_DIRNAME}"
    SRC=${WORKDIR}/${APP_NAME}
    bbplain "****** Begin to build busybox..."
    bbplain "****** Checking source directory: ${SRC}"
    if [ -d "${SRC}" ]; then
        cd "${SRC}"
        #bbplain "${PATH}"
        bbplain "****** Do patch for busybox"
        TagFile=".patched"
        if [ -f ${TagFile} ]; then
            bbplain "  *** have been patched, need do nothing!"
        else
            patch -Np1 -i ${FILE_DIRNAME}/patches/01_${ARCH}.diff
            patch -Np1 -i ${FILE_DIRNAME}/patches/02_*.diff
            touch ${TagFile}
        fi
        busybox_sync_makefile_toolchain ${SRC}/Makefile

        bbplain "****** Copy ${BUSYBOX_LINK} config for busybox"
        if [ "${BUSYBOX_LINK}" = "dynamic" ]; then
            cp ${FILE_DIRNAME}/conf/def_config_dynamic ${SRC}/.config
        else
            cp ${FILE_DIRNAME}/conf/def_config ${SRC}/.config
        fi

        bbplain "****** Compile busybox"
        export FILE_DIRNAME="${FILE_DIRNAME}"
        export CROSS_COMPILE="${RTT_CC_PREFIX}"
        unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS

        # Build syscall stub for dynamic linking. Some RT-Smart musl runtimes
        # do not export syscall, while busybox calls syscall() directly.
        if [ "${BUSYBOX_LINK}" = "dynamic" ]; then
            bbplain "****** Installing syscall stub into libbb"
            cp ${FILE_DIRNAME}/sdk/syscall_stub.c ${SRC}/libbb/
            grep -q '^lib-y += syscall_stub.o$' ${SRC}/libbb/Kbuild.src || \
                echo "lib-y += syscall_stub.o" >> ${SRC}/libbb/Kbuild.src
            rm -f ${SRC}/libbb/Kbuild
        fi

        if [ "${BUSYBOX_LINK}" = "dynamic" ]; then
            make V=1
        else
            make V=1 CONFIG_STATIC=y
        fi
        bbplain "****** Install busybox"
        make install CROSS_COMPILE="${RTT_CC_PREFIX}"
        bbplain "****** Create rootfs img"
        if [ ! -d "${TOPDIR}/${MACHINE}" ]; then
            mkdir ${TOPDIR}/${MACHINE}
        fi
        cp ${FILE_DIRNAME}/conf/inittab ${SRC}
        do_create_ext4img
        bbplain "****** Install ext4.img to: ${TOPDIR}/${MACHINE}"
    else
        bbfatal "Error: ${SRC} not found!"
    fi
}

do_create_ext4img() {
    cd ${WORKDIR}/${APP_NAME}

    bbplain "   *** Create rootfs dir"
    if [ -d rootfs ]; then
        rm -rf rootfs
    fi
    mkdir rootfs
    cp -ra install/* rootfs
    cd rootfs
    mkdir -p dev/shm etc lib mnt proc root run services tmp var
    cp ../inittab etc/

    if [ "${BUSYBOX_LINK}" = "dynamic" ]; then
        bbplain "   *** Copy musl shared libraries"
        smart_copy_musl_runtime lib

        bbplain "   *** Copy RT-Thread Smart libraries"
        smart_copy_rtthread_runtime lib "${FILE_DIRNAME}/sdk/rt-thread"
    fi
    cd var
    ln -s ../run run
    cd ../..

    bbplain "   *** Create ext4.img"
    smart_make_ext4_image rootfs ext4.img 256
    cp ext4.img ${TOPDIR}/${MACHINE}
}

addtask do_build_rootfs after do_unpack
