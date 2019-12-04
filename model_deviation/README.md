# Requirements for windows install
+ Install [git](https://git-scm.com)
+ Install [make](http://gnuwin32.sourceforge.net/packages/make.htm)
+ Install [java](https://adoptopenjdk.net)
+ Install [maven](https://maven.apache.org)
+ Install [gcc](http://mingw-w64.org)

# Download
Download and unpack [Ipopt](https://www.coin-or.org/Ipopt/documentation/node10.html) into `$IPOPT_INSTALL_DIR` of your choice.

# Build Ipopt
```
mkdir $IPOPT_INSTALL_DIR/build
cd $IPOPT_INSTALL_DIR/build
$IPOPT_INSTALL_DIR/configure
make
```

# Build Ipopt java native interface
```
cd $IPOPT_INSTALL_DIR/build/Ipopt/contrib/JavaInterface
make
```

# Link 
Create the folowing symbolink links :
+ `$IPOPT_INSTALL_DIR/build/Ipopt/contrib/JavaInterface/ipopt.jar` -> `lib/ipopt.jar`

## Windows
+ `$IPOPT_INSTALL_DIR/build/Ipopt/contrib/JavaInterface/lib/libjipopt.dll` -> `lib/libjipopt.dll`

## Linux
+ `$IPOPT_INSTALL_DIR/build/Ipopt/contrib/JavaInterface/lib/libjipopt.so` -> `lib/libjipopt.so`

# Build project
`mvn clean install`

