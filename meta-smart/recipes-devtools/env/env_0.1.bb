inherit region-source
inherit machine

DESCRIPTION = "RT-Thread Smart Environment Setup"
LICENSE = "CLOSED"

SRC_URI_GITEE = "git://gitee.com/RT-Thread-Mirror/env.git;branch=master;protocol=https;name=env \
                 git://gitee.com/RT-Thread-Mirror/packages.git;branch=master;protocol=https;name=packages \
                 git://gitee.com/RT-Thread-Mirror/sdk.git;branch=main;protocol=https;name=sdk \
"

SRC_URI_GITHUB = "git://github.com/RT-Thread/env.git;branch=master;protocol=https;name=env \
                  git://github.com/RT-Thread/packages.git;branch=master;protocol=https;name=packages \
                  git://github.com/RT-Thread/sdk.git;branch=main;protocol=https;name=sdk \
"

python () {
    import os

    handle_machine(d)

    local_env = os.path.expanduser("~/.env/tools/scripts")
    local_env_sh = os.path.expanduser("~/.env/env.sh")
    if os.path.isdir(local_env) and os.path.isfile(local_env_sh):
        d.setVar('S', local_env)
        d.setVar('SRC_URI', '')
    else:
        set_preferred_source(d)

    os.environ['ARCH'] = d.getVar('ARCH')
    os.environ['RTT_CC_PREFIX'] = d.getVar('RTT_CC_PREFIX')
    os.environ['BSP'] = d.getVar('BSP')
}

SRCREV_env = "AUTOINC"
SRCREV_packages = "AUTOINC"
SRCREV_sdk = "AUTOINC"

SRCREV_FORMAT = "env_packages_sdk"

S = "${WORKDIR}/git"

do_install_env() {
    # Skip if env tools are already installed
    if [ -d ~/.env/tools/scripts ] && [ -f ~/.env/env.sh ]; then
        bbplain "****** RT-Thread env tools already installed, skipping"
        return 0
    fi

    bbplain "****** Installing RT-Thread environment"

    mkdir -p ~/.env/local_pkgs ~/.env/packages ~/.env/tools

    if [ -d "${WORKDIR}/sources-unpack/env" ]; then
        cp -r ${WORKDIR}/sources-unpack/env ~/.env/tools/scripts
        cp ${WORKDIR}/sources-unpack/env/env.sh ~/.env/
        cp ${WORKDIR}/sources-unpack/env/Kconfig ~/.env/tools/
    elif [ -d "${S}" ]; then
        cp -r ${S} ~/.env/tools/scripts
        [ -f ${S}/env.sh ] && cp ${S}/env.sh ~/.env/
        [ -f ${S}/Kconfig ] && cp ${S}/Kconfig ~/.env/tools/
    else
        bbfatal "Source directory not found: ${S} or ${WORKDIR}/sources-unpack/env"
    fi

    if [ -d "${WORKDIR}/sources-unpack/packages" ]; then
        cp -r ${WORKDIR}/sources-unpack/packages ~/.env/packages/packages
        echo "source \"\$PKGS_DIR/packages/Kconfig\"" > ~/.env/packages/Kconfig
    fi
    if [ -d "${WORKDIR}/sources-unpack/sdk" ]; then
        cp -r ${WORKDIR}/sources-unpack/sdk ~/.env/packages/sdk
    fi
}

addtask do_install_env after do_unpack 
