inherit region-source
inherit machine

DESCRIPTION = "MicroPython Unix port for RT-Thread Smart"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=5f1c6c6e2c4e4d3a1b0e9f3c8d7e2a1b"

APP_NAME = "micropython"
APP_VERSION = "1.22.0"

# MicroPython uses its own Makefile-based build system (ports/unix/).
# We point S at the local source tree when available; when not, the
# source is fetched from GitHub / Gitee.

SRC_URI_GITEE = "git://gitee.com/mirrors/micropython.git;branch=master;protocol=https"
SRC_URI_GITHUB = "git://github.com/micropython/micropython.git;branch=master;protocol=https"

BB_GIT_SHALLOW = "1"
BB_GIT_SHALLOW_DEPTH = "1"

SRCREV = "AUTOINC"

LAYERDIR_smart = "${@os.path.dirname(os.path.dirname(os.path.dirname(d.getVar('FILE', True))))}"
SMARTROOT = "${@os.path.dirname(d.getVar('LAYERDIR_smart', True))}"

python () {
    import os

    set_preferred_source(d)

    local_mpy = os.path.join(d.getVar('SMARTROOT'), 'micropython')

    if os.path.lexists(local_mpy):
        if not os.path.isdir(os.path.join(local_mpy, 'ports')):
            bb.fatal("Local micropython path exists but does not look like a micropython source tree: %s" % local_mpy)
        d.setVar('S', local_mpy)
        d.setVar('SRC_URI', '')
    else:
        rtthread_src = os.path.join(d.getVar('WORKDIR', True), 'git')
        d.setVar('S', rtthread_src)

    toolchain_for_machine(d)
}

# MicroPython Unix port build configuration.
# Disable features that require external libraries not available on RT-Thread Smart.
# qemuriscv64 uses the soft-float riscv64-linux-musleabi- toolchain
# (rv64imafdc, lp64, no V extension) — the standard crt1.o and libc.a
# work correctly without workarounds.
MPY_STACK_CHECK ?= "0"
MPY_USE_READLINE ?= "0"
MPY_PY_THREAD   ?= "0"
MPY_PY_FFI      ?= "0"
MPY_PY_BTREE    ?= "0"
MPY_PY_SSL      ?= "0"

do_compile() {
    bbplain "##############################"
    bbplain "****** Begin to build micropython..."
    bbplain "****** Source: ${S}"

    if [ ! -d "${S}/ports/unix" ]; then
        bbfatal "MicroPython Unix port not found: ${S}/ports/unix"
    fi

    cd "${S}"

    # Ensure mpy-cross is built for the host (not cross-compiled)
    bbplain "****** Building mpy-cross for host"
    make -C mpy-cross clean
    make -C mpy-cross V=1

    bbplain "****** Cross-compiling MicroPython Unix port"
    export PATH="${TOPDIR}/toolchains/${TARGET_TC}/bin:${PATH}"

    unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS

    make -C ports/unix \
        CROSS_COMPILE="${RTT_CC_PREFIX}" \
        CFLAGS_EXTRA="-Wno-clobbered" \
        LDFLAGS_EXTRA="-static" \
        MICROPY_STACK_CHECK=${MPY_STACK_CHECK} \
        MICROPY_USE_READLINE=${MPY_USE_READLINE} \
        MICROPY_PY_THREAD=${MPY_PY_THREAD} \
        MICROPY_PY_FFI=${MPY_PY_FFI} \
        MICROPY_PY_BTREE=${MPY_PY_BTREE} \
        MICROPY_PY_SSL=${MPY_PY_SSL} \
        V=1

    bbplain "****** MicroPython build completed"
}

do_install() {
    bbplain "****** Installing micropython to ${TOPDIR}/${MACHINE}"

    if [ ! -d "${TOPDIR}/${MACHINE}" ]; then
        mkdir -p "${TOPDIR}/${MACHINE}"
    fi

    # Copy the micropython binary
    install -m 0755 "${S}/ports/unix/build-standard/micropython" "${TOPDIR}/${MACHINE}/micropython"

    # Copy the standard library
    if [ -d "${TOPDIR}/${MACHINE}/lib/micropython" ]; then
        rm  -rf "${TOPDIR}/${MACHINE}/lib/micropython"
    fi
    mkdir -p "${TOPDIR}/${MACHINE}/lib/micropython"

    # Copy MicroPython standard library modules
    cp -r "${S}/extmod"       "${TOPDIR}/${MACHINE}/lib/micropython/"
    cp -r "${S}/lib/micropython-lib/python-stdlib" "${TOPDIR}/${MACHINE}/lib/micropython/"
    cp -r "${S}/lib/micropython-lib/unix-ffi"      "${TOPDIR}/${MACHINE}/lib/micropython/"

    bbplain "****** MicroPython installed to ${TOPDIR}/${MACHINE}"
}

addtask do_install after do_compile
