Price Ratio Analyser
====================

Run
===
    sbt "run src/test/resources/test_data_small.tsv" 
 
to analyse the sample data from the description. Alternatively, 
you can build a package using native packager and install the package: 

    sbt debian:packageBin
    sudo dpkg -i target/price-ratio-analyser_0.1-SNAPSHOT_all.deb
    
and use the `price-ratio-analyser` command:
    
    price-ratio-analyser <filename>



    
