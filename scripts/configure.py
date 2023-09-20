# This script generates and updates project configuration files.

# Run this script with rvscaffold in PYTHONPATH
import rvscaffold as scaffold

class Project(scaffold.Java):
    def script_path_text(self): return __file__
    def repository_name(self): return 'foxcache'
    def pretty_name(self): return 'Fox Cache'
    def pom_description(self): return "Reactive persistent cache for applications based on Hookless and especially PushMode."
    def inception_year(self): return 2020
    def jdk_version(self): return 17
    def has_javadoc(self): return False
    def stagean_annotations(self): return True
    
    def dependencies(self):
        yield from super().dependencies()
        yield self.use_closeablescope()
        yield self.use_noexception()
        yield self.use_noexception_slf4j()
        yield self.use_hookless()
        yield self.use_ladybugformatters()
        yield self.use_meerkatwidgets()
        yield self.use_streamex()
        # Used to serialize cache content output.
        yield self.use('com.esotericsoftware:kryo:5.2.0')
        yield self.use_gson()
        yield self.use_junit()
        yield self.use_slf4j_test()
    
    def javadoc_links(self):
        yield 'https://stagean.machinezoo.com/javadoc/'
        yield 'https://closeablescope.machinezoo.com/javadoc/'
        yield 'https://noexception.machinezoo.com/javadocs/core/'
        # Kryo javadoc?

Project().generate()
