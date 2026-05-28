inherit machine

DESCRIPTION = "RT-Smart musl toolchain"
LICENSE = "GPL-3.0-with-GCC-exception & GPL-3.0-only"

#关闭校验
BB_STRICT_CHECKSUM = "0"

python do_install_toolchain() {
    import os
    import shutil

    tc_url = toolchain_for_machine(d)
    prefix = d.getVar("RTT_CC_PREFIX")
    local_tc_path = smart_find_host_toolchain(d, prefix)
    def_toolchain_path = os.path.join(d.getVar('TOPDIR'), "toolchains", d.getVar("TARGET_TC"))
    tc_bin = os.path.join(def_toolchain_path, "bin", prefix + "gcc")
    tpath = os.path.join(d.getVar('TOPDIR'), "toolchains")

    def link_toolchain(src, dst):
        if not os.path.exists(tpath):
            os.makedirs(tpath)
        if os.path.lexists(dst):
            if os.path.islink(dst) or os.path.isfile(dst):
                os.unlink(dst)
            else:
                shutil.rmtree(dst)
        os.symlink(src, dst)

    # 首先检查目标目录是否已存在
    if os.path.lexists(def_toolchain_path):
        if os.path.exists(tc_bin):
            bb.plain("##############################")
            bb.plain("****** Find default toolchain:")
            bb.plain(def_toolchain_path)
            bb.plain("****** Need do nothing!")
            return

        bb.plain("##############################")
        bb.plain("****** Existing toolchain does not match expected compiler:")
        bb.plain("****** " + tc_bin)
        if os.path.islink(def_toolchain_path):
            os.unlink(def_toolchain_path)
        else:
            shutil.rmtree(def_toolchain_path)

    # 检查本地工具链是否存在
    if local_tc_path:
        bb.plain("##############################")
        bb.plain("****** Find host toolchain:")
        bb.plain(local_tc_path)
        bb.plain("****** Creating symbolic link to local toolchain")
        link_toolchain(local_tc_path, def_toolchain_path)
        bb.plain("****** Symbolic link created successfully")
        return

    archive = smart_find_download_archive(
        d,
        names=[
            d.getVar("TARGET_TC") + "_latest.tar.bz2",
            d.getVar("TARGET_TC") + ".tar.bz2",
            d.getVar("LOCAL_TC") + ".tar.bz2",
        ],
        patterns=[
            d.getVar("TARGET_TC") + "*.tar*",
            d.getVar("LOCAL_TC") + "*.tar*",
        ],
    )
    if archive:
        bb.plain("##############################")
        bb.plain("****** Find downloaded toolchain archive:")
        bb.plain(archive)
        if not os.path.exists(tpath):
            os.makedirs(tpath)
        bb.plain("****** Begin unpacking local archive...")
        shutil.unpack_archive(archive, tpath)

        if os.path.exists(tc_bin):
            bb.plain("****** Install smart-gcc toolchain from local archive done.")
            return

        host_tc = smart_find_host_toolchain(d, prefix)
        if host_tc:
            bb.plain("****** Local archive unpacked as reusable host-style toolchain:")
            bb.plain(host_tc)
            link_toolchain(host_tc, def_toolchain_path)
            return

        unpacked = os.path.join(tpath, d.getVar("LOCAL_TC"))
        if os.path.exists(unpacked) and smart_toolchain_compiler(unpacked, prefix):
            link_toolchain(unpacked, def_toolchain_path)
            return

        bb.fatal("Local toolchain archive did not provide expected compiler: %s" % tc_bin)

    # 如果本地工具链和下载缓存都不存在，则下载
    bb.plain("##############################")
    bb.plain("****** Not find host/default toolchain or local archive, download and unpack to: " + tpath)
    uri = tc_url.split()
    fetcher = bb.fetch2.Fetch(uri, d)
    bb.plain("****** Begin downloading...")
    fetcher.download()
    bb.plain("****** Begin unpacking...")
    if not os.path.exists(tpath):
        os.makedirs(tpath)
    fetcher.unpack(tpath)

    if os.path.exists(tc_bin):
        bb.plain("****** Install smart-gcc toolchain done.")
        return

    host_tc = smart_find_host_toolchain(d, prefix)
    if host_tc:
        bb.plain("****** Downloaded toolchain unpacked as reusable host-style toolchain:")
        bb.plain(host_tc)
        link_toolchain(host_tc, def_toolchain_path)
        return

    bb.fatal("Downloaded toolchain did not provide expected compiler: %s" % tc_bin)
}

addtask do_install_toolchain
