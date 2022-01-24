# This script generates and updates project configuration files.

# We are assuming that project-config is available in sibling directory.
# Checkout from https://github.com/robertvazan/project-config
import os.path
import sys
sys.path.append(os.path.normpath(os.path.join(__file__, '../../../project-config/src')))

from java import *

project_script_path = __file__
repository_name = lambda: 'pmdata'
pretty_name = lambda: 'PMData'
pom_description = lambda: "Framework for reactive data-driven and data science websites."
inception_year = lambda: 2020
jdk_version = lambda: 17
has_javadoc = lambda: False
stagean_annotations = lambda: True
project_status = lambda: experimental_status()

def dependencies():
    use_pmsite()
    use_streamex()
    # Used to serialize cache content output.
    use('com.esotericsoftware:kryo:5.2.0')
    use('org.jfree:jfreechart:1.5.3')
    # Batik is used to render charts into SVG.
    batik_version = '1.14'
    use(f'org.apache.xmlgraphics:batik-dom:{batik_version}')
    use(f'org.apache.xmlgraphics:batik-svggen:{batik_version}')
    use(f'org.apache.xmlgraphics:batik-awt-util:{batik_version}')
    use('com.github.haifengl:smile-plot:2.6.0')
    use_junit()
    use_slf4j_test()

javadoc_links = lambda: [
    'https://stagean.machinezoo.com/javadoc/',
    'https://noexception.machinezoo.com/javadoc/',
    'https://hookless.machinezoo.com/javadocs/core/',
    'https://pushmode.machinezoo.com/javadoc/'
    # PMSite does not have javadoc yet.
    # JFreeChart and Kryo not linked, because automatic modules are not supported by javadoc.
    # Smile plot not linked, because it is not a module yet.
]

generate(globals())
