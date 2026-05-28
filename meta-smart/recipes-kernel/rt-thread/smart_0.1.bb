inherit region-source
inherit machine

DESCRIPTION = "RT-Thread Smart Kernel"
LICENSE = "CLOSED"

SRC_URI_GITEE = "git://gitee.com/rtthread/rt-thread.git;branch=master;protocol=https;name=rtthread \
                 git://gitee.com/RT-Thread-Mirror/lwext4.git;branch=master;protocol=https;name=lwext4;subdir=lwext4 \
"

SRC_URI_GITHUB = "git://github.com/RT-Thread/rt-thread.git;branch=master;protocol=https;name=rtthread \
                  git://github.com/RT-Thread-packages/lwext4.git;branch=master;protocol=https;name=lwext4;subdir=lwext4 \
"

BB_GIT_SHALLOW = "1"
BB_GIT_SHALLOW_DEPTH = "1"

python () {
    import os

    set_preferred_source(d)

    local_rtt = os.path.join(d.getVar('SMARTROOT'), 'rt-thread')

    if os.path.lexists(local_rtt):
        if not os.path.isdir(os.path.join(local_rtt, 'bsp')):
            bb.fatal("Local rt-thread path exists but does not look like an rt-thread source tree: %s" % local_rtt)

        d.setVar('S', local_rtt)
        d.setVar('SRC_URI', '')
        d.setVar('SRCREV_FORMAT', '')
    else:
        uris = (d.getVar('SRC_URI') or '').split()
        formats = ['rtthread', 'lwext4']
        rtthread_archive = smart_find_download_archive(
            d,
            patterns=['*rt-thread*.tar*', '*rtthread*.tar*', '*RT-Thread*.tar*'],
        )
        lwext4_archive = smart_find_download_archive(
            d,
            patterns=['*lwext4*.tar*'],
        )

        if rtthread_archive:
            d.setVar('LOCAL_RTTHREAD_ARCHIVE', rtthread_archive)
            d.setVar('S', os.path.join(d.getVar('WORKDIR', True), 'local-rt-thread'))
            uris = [uri for uri in uris if 'name=rtthread' not in uri]
            formats.remove('rtthread')
        else:
            d.setVar('S', os.path.join(d.getVar('WORKDIR', True), 'git'))

        if lwext4_archive:
            d.setVar('LOCAL_LWEXT4_ARCHIVE', lwext4_archive)
            uris = [uri for uri in uris if 'name=lwext4' not in uri]
            formats.remove('lwext4')

        d.setVar('SRC_URI', ' '.join(uris))
        d.setVar('SRCREV_FORMAT', '_'.join(formats))

    toolchain_for_machine(d)
}

SRCREV_rtthread = "AUTOINC"
SRCREV_lwext4 = "AUTOINC"

SRCREV_FORMAT = "rtthread_lwext4"

# 使用 LAYERDIR 来定位 meta-smart 层的位置
LAYERDIR_smart = "${@os.path.dirname(os.path.dirname(os.path.dirname(d.getVar('FILE', True))))}"
# 通过 LAYERDIR 向上一级找到项目根目录
SMARTROOT = "${@os.path.dirname(d.getVar('LAYERDIR_smart', True))}"

python do_prepare_local_sources() {
    import os
    import shutil

    def extract_archive(archive, dest, marker):
        if not archive:
            return
        if os.path.isdir(os.path.join(dest, marker)):
            bb.plain("****** Reusing prepared local source: %s" % dest)
            return

        tmp = dest + ".unpack"
        if os.path.exists(tmp):
            shutil.rmtree(tmp)
        if os.path.exists(dest):
            shutil.rmtree(dest)
        os.makedirs(tmp)

        bb.plain("****** Unpacking local source archive:")
        bb.plain("****** " + archive)
        shutil.unpack_archive(archive, tmp)

        candidates = []
        for base, dirs, files in os.walk(tmp):
            if marker in dirs:
                candidates.append(base)
        if not candidates:
            bb.fatal("Archive does not look like expected source tree (%s): %s" % (marker, archive))

        src = sorted(candidates, key=lambda path: (path.count(os.sep), path))[0]
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        if src == tmp:
            os.makedirs(dest)
            for name in os.listdir(tmp):
                shutil.move(os.path.join(tmp, name), os.path.join(dest, name))
            shutil.rmtree(tmp)
        else:
            shutil.move(src, dest)
            shutil.rmtree(tmp)

    extract_archive(d.getVar('LOCAL_RTTHREAD_ARCHIVE'), d.getVar('S'), 'bsp')
    extract_archive(
        d.getVar('LOCAL_LWEXT4_ARCHIVE'),
        os.path.join(d.getVar('WORKDIR'), 'sources-unpack', 'lwext4'),
        'src',
    )
}

smart_sync_kernel_abi() {
    case "${ARCH}:${RTT_CC_PREFIX}" in
        riscv64:riscv64-linux-musleabi-)
            if [ ! -f "${SCONS_BUILD_DIR}/rtconfig.py" ]; then
                bbfatal "rtconfig.py not found: ${SCONS_BUILD_DIR}/rtconfig.py"
            fi

            bbplain "****** Align RISC-V kernel ABI with ${RTT_CC_PREFIX}: lp64"
            SMART_KERNEL_ABI_BACKUP="${WORKDIR}/${MACHINE}-rtconfig.py.orig"
            cp "${SCONS_BUILD_DIR}/rtconfig.py" "$SMART_KERNEL_ABI_BACKUP"
            sed -i 's/-mabi=lp64d/-mabi=lp64/g' "${SCONS_BUILD_DIR}/rtconfig.py"
            ;;
    esac
}

smart_restore_kernel_abi() {
    if [ -n "${SMART_KERNEL_ABI_BACKUP:-}" ] && [ -f "$SMART_KERNEL_ABI_BACKUP" ]; then
        cp "$SMART_KERNEL_ABI_BACKUP" "${SCONS_BUILD_DIR}/rtconfig.py"
        rm -f "$SMART_KERNEL_ABI_BACKUP"
        bbplain "****** Restored kernel ABI config source"
    fi
}

smart_sync_kernel_config() {
    rtconfig="${SCONS_BUILD_DIR}/rtconfig.h"

    if [ ! -f "$rtconfig" ]; then
        bbplain "ERROR: rtconfig.h not found after pyconfig: $rtconfig"
        return 1
    fi

    if grep -q '^CONFIG_RT_USING_DFS_LWEXT4=y' "${FILE_DIRNAME}/${MACHINE}_defconfig"; then
        if [ ! -f "${SCONS_BUILD_DIR}/packages/lwext4-latest/SConscript" ]; then
            bbplain "ERROR: lwext4 package source not found for ${MACHINE}: ${SCONS_BUILD_DIR}/packages/lwext4-latest"
            return 1
        fi

        if ! grep -q '^#define RT_USING_DFS_LWEXT4$' "$rtconfig"; then
            bbplain "****** Preserve lwext4 DFS config requested by ${MACHINE}_defconfig"
            sed -i \
                -e '/^#endif$/i #define PKG_USING_LWEXT4' \
                -e '/^#endif$/i #define RT_USING_DFS_LWEXT4' \
                "$rtconfig"
        fi
    fi
}

smart_build_kernel_image() {
    scons --pyconfig-silent -C ${SCONS_BUILD_DIR} || return $?
    smart_sync_kernel_config || return $?

    bbplain "****** Build rt-smart kernel"
    scons -C ${SCONS_BUILD_DIR} || return $?
}

do_build_kernel() {
    bbplain "##############################"
    export RTT_CC="gcc"
    export RTT_EXEC_PATH="${TOPDIR}/toolchains/${TARGET_TC}/bin"
    export SCONS_BUILD_DIR="${S}/${BSP}"
    bbplain "****** rt-thread source: ${S}"
    bbplain "****** BSP build dir: ${SCONS_BUILD_DIR}"
    bbplain "****** toolchain path: ${RTT_EXEC_PATH}"

    # Clear OE-core compiler flags that inject unsupported -fcanon-prefix-map
    unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS

    if [ "${S}" = "${SMARTROOT}/rt-thread" ]; then
        bbplain "****** Using local rt-thread directory"
    else
        bbplain "****** Using downloaded rt-thread source"
    fi

    if [ ! -d "${SCONS_BUILD_DIR}" ]; then
        bbfatal "BSP directory not found: ${SCONS_BUILD_DIR}"
    fi

    if [ "${S}" != "${SMARTROOT}/rt-thread" ]; then
        if [ -d ${SCONS_BUILD_DIR}/packages ]; then
            rm -rf ${SCONS_BUILD_DIR}/packages/lwext4*
        else
            mkdir -p ${SCONS_BUILD_DIR}/packages
        fi

        if [ ! -d "${WORKDIR}/sources-unpack/lwext4" ]; then
            bbfatal "lwext4 source not found in ${WORKDIR}/sources-unpack/lwext4"
        fi
        cp -r ${WORKDIR}/sources-unpack/lwext4 ${SCONS_BUILD_DIR}/packages/lwext4-latest
        cp ${FILE_DIRNAME}/lwext4_SConscript ${SCONS_BUILD_DIR}/packages/SConscript
    fi

    bbplain "****** Copy default config for ${MACHINE}"
    cp ${FILE_DIRNAME}/${MACHINE}_defconfig ${SCONS_BUILD_DIR}/.config

    smart_sync_kernel_abi

    set +e
    smart_build_kernel_image
    kernel_status=$?
    set -e
    smart_restore_kernel_abi
    if [ "$kernel_status" -ne 0 ]; then
        bbfatal "RT-Thread kernel build failed for ${MACHINE}"
    fi

    bbplain "****** Install rt-smart kernel to: ${TOPDIR}/${MACHINE}"
    if [ ! -d "${TOPDIR}/${MACHINE}" ]; then
        mkdir ${TOPDIR}/${MACHINE}
    fi
    cp ${SCONS_BUILD_DIR}/rtthread.bin ${TOPDIR}/${MACHINE}

    # copy the unified QEMU script
    if [ -f "${SMARTROOT}/tools/run-qemu" ]; then
        bbplain "****** Copy QEMU script"
        install -m 0755 "${SMARTROOT}/tools/run-qemu" "${TOPDIR}/${MACHINE}/"
        bbplain "****** Successfully copied QEMU script"
    fi
}
do_build_kernel[depends] = "smart-gcc:do_install_toolchain env:do_install_env"

# build kernel and rootfs 
do_build_all() {
    do_build_kernel
    bbplain "****** All build down! You can enter ${TOPDIR}/${MACHINE} to run qemu."
}
do_build_all[depends] = "busybox:do_build_rootfs"

do_build_mini_rootfs() {
    bbplain "****** Mini rootfs image is ready: ${TOPDIR}/${MACHINE}/mini_rootfs.img"
}
do_build_mini_rootfs[depends] = "mini-rootfs:do_build_mini_rootfs"

do_clean[depends] = "smart-gcc:do_clean"
do_clean[depends] += "busybox:do_clean"
do_clean[depends] += "env:do_clean"

do_cleansstate[depends] = "smart-gcc:do_cleansstate"
do_cleansstate[depends] += "busybox:do_cleansstate"
do_cleansstate[depends] += "env:do_cleansstate"

do_cleanall[depends] = "smart-gcc:do_cleanall"
do_cleanall[depends] += "busybox:do_cleanall"
do_cleanall[depends] += "env:do_cleanall"

addtask do_prepare_local_sources after do_unpack before do_build_kernel
addtask do_build_kernel after do_prepare_local_sources
addtask do_build_all after do_prepare_local_sources
addtask do_build_mini_rootfs
