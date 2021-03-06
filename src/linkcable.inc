
static private int LINKmulti = 1;
static private int LINKdelay = 0; // do not change, use setDelay() instead

static private int LINKcntdwn = 0;
static private int[] LINKbuf = new int[8];
static private int LINKind = 0;
static private int LINKtimeout = 0;

static protected int LinkCableStatus = 0; // 0 = offline, 1 = server , 2 = client

#ifndef USE_JAVA_MOBILE

static void setDelay(int ndelay) throws IOException {
	for (int i = 0; i < LINKdelay; ++i)
		LinkCableIn.readInt();
	LINKdelay = ndelay;
	for (int i = 0; i < LINKdelay; ++i)
		LinkCableOut.writeInt(0);
	LINKmulti = LINKdelay + 1;
}

static protected ServerSocket LinkCablesrvr = null;

static protected Socket LinkCablesktOut = null;
static protected Socket LinkCablesktIn = null;

static protected DataInputStream LinkCableIn = null;
static protected DataOutputStream LinkCableOut = null;

static protected boolean LinkCableSendReceive=false;


public static final void severLink() {
	try {
		if(LinkCablesrvr!=null) {
			LinkCablesrvr.close();
			LinkCablesrvr=null;
		}
		if(LinkCablesktOut!=null) {
			LinkCablesktOut.close();
			LinkCablesktOut=null;
		}
		if(LinkCablesktIn!=null) {
			LinkCablesktIn.close();
			LinkCablesktIn=null;
		}
		if(LinkCableIn!=null) {
			LinkCableIn.close();
			LinkCableIn=null;
		}
		if(LinkCableOut!=null) {
			LinkCableOut.close();
			LinkCableOut=null;
		}
	}
	catch(IOException e) {
		PRINTLN("Error while closing socket(s)");
		e.printStackTrace();
	}
	finally {
		LinkCableStatus=0;
	}
}

public static final void serveLink() throws IOException {
	if(LinkCableStatus==0) {
		LinkCablesrvr = new ServerSocket(LINK_CABLE_TCP_PORT);
		LinkCablesktOut = LinkCablesrvr.accept();
		PRINTLN("Connection established");
		LinkCablesktOut.setTcpNoDelay(true);
		LinkCableIn = new DataInputStream(LinkCablesktOut.getInputStream());
		LinkCableOut = new DataOutputStream(LinkCablesktOut.getOutputStream());
		LinkCableStatus=1;
		setDelay(0);
	}
	else throw new IOException("WARNING: Can't serve while not offline");
}

public static final void clientLink(String target)  throws IOException {
	if(LinkCableStatus==0) {
		LinkCablesktIn = new Socket(target, LINK_CABLE_TCP_PORT);
		LinkCablesktIn.setTcpNoDelay(true);
		LinkCableIn = new DataInputStream(LinkCablesktIn.getInputStream());
		LinkCableOut = new DataOutputStream(LinkCablesktIn.getOutputStream());
		LinkCableStatus=2;
		setDelay(0);
	}
	else throw new IOException("WARNING: Can't client while not offline");
}
#endif
