import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

public class VideoController {
	private static final int MIN_WIDTH  = 160;
	private static final int MIN_HEIGHT = 144;

	private JPanel listener = null;
	private BufferedImage drawImg[];
	private int curDrawImg = 0;
	private int blitImg[][]=new int[144][160]; // [y][x]

	private int CurrentVRAMBank=0;
	//private int VRAM[][]=new int[2][0x2000]; //8k per bank;
	protected int VRAM[]=new int[0x4000]; //8k per bank;
	protected int OAM[]=new int[0xa0]; //Sprite Attribute Table;

	protected boolean isCGB;

	protected int LY=0;
	protected int LYC=0;
	protected int SCX=0;
	protected int SCY=0;
	protected int WX=0;
	protected int WY=0;
	protected int LCDC=0;
	protected int STAT=0; // FF41 - STAT - LCDC Status (R/W)

	protected int GRAYSHADES[][] = { {0xFF, 0xFF, 0xFF},   // WHITE
	                                 {0xD3, 0xD3, 0xD3},   // LIGHTGRAY
	                                 {0xBE, 0xBE, 0xBE},   // DARKGRAY
	                                 {0x00, 0x00, 0x00} }; // BLACK

	protected int BGPI=0;              //BCPS/BGPI - CGB Mode Only - Background Palette Index
	private int BGPD[]=new int[8*4*2]; //BCPD/BGPD - CGB Mode Only - Background Palette Data

	protected int OBPI=0;              //OCPS/OBPI - CGB Mode Only - Sprite Palette Index
	private int OBPD[]=new int[8*4*2]; //OCPD/OBPD - CGB Mode Only - Sprite Palette Data

	/* caching vars */
	private int colors[][] = new int[8*4*2][3*4];
	private int patpix[][][] = new int[4096][8][8]; // see updatepatpix()
	private boolean patdirty[] = new boolean[1024]; // see updatepatpix()
	private boolean anydirty = true;                // see updatepatpix()
	private boolean alldirty = true;                // see updatepatpix()

	private CPU cpu; // dont think we need this... //yes we do, we need interrupts

	private sun.misc.Perf perf;
	private long pfreq;
	private long ptick;
	private long ftick;

	private int scale = 2;

	public VideoController(CPU cpu, int image_width, int image_height) {
		this.cpu = cpu;
		drawImg=new BufferedImage[2];
		scale (image_width, image_height);
		this.isCGB = cpu.isCGB();
		perf = sun.misc.Perf.getPerf();
		pfreq = perf.highResFrequency();
		lastms = perf.highResCounter();
		
		long x = System.nanoTime();
	}

	final public void addListener(JPanel panel)
	{
		listener = panel; // only 1 listener at a time currently :-p
		//drawImg[0]=panel.createVolatileImage(160, 144);
		//drawImg[1]=panel.createVolatileImage(160, 144);
		//drawImg[1]=new BufferedImage(160, 144, BufferedImage.TYPE_3BYTE_BGR);
	}

	public void scale(int width, int height) {
		if (width <  scale*MIN_WIDTH)  width  = scale*MIN_WIDTH;
		if (height < scale*MIN_HEIGHT) height = scale*MIN_HEIGHT;

		drawImg[0]=new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		drawImg[1]=new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
	}

	final public Image getImage() {
		// return ((VolatileImage)drawImg[curDrawImg]).getScaledInstance(image_width, image_height,Image.SCALE_FAST); // display image not being drawn to
		return drawImg[curDrawImg]; // display image not being drawn to
	}

	final private void drawPixel(int x, int y, int pal, int col) {
		blitImg[y][x] = (pal << 2) | col;
	}

	final private void palQuadify(int[] col) {
		for (int i=0; i<3; ++i) {
			col[3+i] = col[i];
			col[6+i] = col[i];
			col[9+i] = col[i];
		}
	}

	final private void palChange(int palcol, int r, int g, int b) {
		colors[palcol][0] = r;
		colors[palcol][1] = g;
		colors[palcol][2] = b;

		palQuadify(colors[palcol]);
		//colors[palcol] = new Color(r,g,b);
	}

	//static long lastms = System.nanoTime();
	static long lastms;
	static int fps23fix=0;
	final private void blitImage() {
		//Graphics g = drawImg[curDrawImg^1].getGraphics();
		WritableRaster wr = drawImg[curDrawImg^1].getRaster();
		if (scale == 1) {
			for (int y = 0; y < 144; ++y) {
				int blitLine[] = blitImg[y];
				for (int x = 0; x < 160; ++x) {
					int col = blitLine[x];
					if ((col >= 0) && (col < (8*4*2)))
						wr.setPixel(x,y, colors[col]);
				}
			}
		}
		else if (scale == 2) {
			for (int y = 1; y < 143; ++y) {
				int blitLine2[] = blitImg[y];
				int blitLine1[] = blitImg[y-1];
				int blitLine3[] = blitImg[y+1];
				for (int x = 1; x < 159; ++x) {
					if ((blitLine2[x-1] != blitLine2[x+1])
					&& (blitLine1[x]   != blitLine3[x])) {
						wr.setPixel(x*2  ,y*2  , colors[(blitLine1[x] == blitLine2[x-1]) ? blitLine2[x-1] : blitLine2[x]]);
						wr.setPixel(x*2+1,y*2  , colors[(blitLine1[x] == blitLine2[x+1]) ? blitLine2[x+1] : blitLine2[x]]);
						wr.setPixel(x*2  ,y*2+1, colors[(blitLine3[x] == blitLine2[x-1]) ? blitLine2[x-1] : blitLine2[x]]);
						wr.setPixel(x*2+1,y*2+1, colors[(blitLine3[x] == blitLine2[x+1]) ? blitLine2[x+1] : blitLine2[x]]);
					}
					else {
						wr.setPixels(x*2,y*2, 2,2,colors[blitLine2[x]]);
					}
				}
			}
		}
		curDrawImg ^= 1;

		long ct;
		do {
			ct = perf.highResCounter();
		} while ((ct-lastms) < 1000000/60);
		lastms = ct;
	}

	final public void setMonoColData(int index, int value) {
		// index = 0 -> FF47 - BGP - BG Palette Data (R/W) - Non CGB Mode Only
		// index = 1 -> FF48 - OBP0 - Object Palette 0 Data (R/W) - Non CGB Mode Only
		// index = 2 -> FF49 - OBP1 - Object Palette 1 Data (R/W) - Non CGB Mode Only

		if (isCGB) return;

		if (index==0) index= (0x20>>2);
		else --index;
		int temp[] = new int[3];
		temp = GRAYSHADES[(value>>0)&3]; palChange((index<<2) | 0, temp[0], temp[1], temp[2]);
		temp = GRAYSHADES[(value>>2)&3]; palChange((index<<2) | 1, temp[0], temp[1], temp[2]);
		temp = GRAYSHADES[(value>>3)&3]; palChange((index<<2) | 2, temp[0], temp[1], temp[2]);
		temp = GRAYSHADES[(value>>6)&3]; palChange((index<<2) | 3, temp[0], temp[1], temp[2]);
	}

	final public void setBGColData(int value) {
		BGPD[BGPI&0x3f] = value;

		// calculate color now
		int base = (BGPI & 0x3e);
		int data = BGPD[base] | (BGPD[base+1]<<8);
		int palnum = base >> 3;
		int colnum = (base >> 1) & 3;
		int r = (data >>  0) & 0x1F;
		int g = (data >>  5) & 0x1F;
		int b = (data >> 10) & 0x1F;

		r <<= 3; r |= (r >> 5);
		g <<= 3; g |= (g >> 5);
		b <<= 3; b |= (b >> 5);
		// TODO gb->vga rgb conv
		// i think that was it... gnuboy doesnt seem
		// to do anything more and there it works
		// fading issue is somethere else, maybe int timing issue?

		palChange((palnum << 2) | colnum | 0x20, r, g, b);

		if ((BGPI&(1<<7))!=0)
			++BGPI;
	}

	final public int getBGColData() {
		return BGPD[BGPI&0x3f];
	}

	final public void setOBColData(int value) {
		OBPD[OBPI&0x3f] = value;

		// calculate color now
		int base = (OBPI & 0x3e);
		int data = OBPD[base] | (OBPD[base+1]<<8);
		int palnum = base >> 3;
		int colnum = (base >> 1) & 3;
		int r = (data >>  0) & 0x1F;
		int g = (data >>  5) & 0x1F;
		int b = (data >> 10) & 0x1F;

		r <<= 3; r |= (r >> 5);
		g <<= 3; g |= (g >> 5);
		b <<= 3; b |= (b >> 5);

		palChange((palnum << 2) | colnum, r, g, b);

		if ((OBPI&(1<<7))!=0)
			++OBPI;
	}

	final public int getOBColData() {
		return OBPD[OBPI&0x3f];
	}

	/** updates contents of patpix[][][] when it is outdated
	 *
	 *  when anydirty is false nothing will be done
	 *  when alldirty is true all patterns will be updated
	 *  when patdirty[i] is true pattern i will be updated
	 *
	 *  patpix[i] is an 8x8 (y,x) matrix with colors of the tile
	 *  i needs to 0..384-1 or 512..896
	 *
	 *  patpix[   0..1024-1] hold normal patterns
	 *  patpix[1024..2048-1] hold patterns flipped x-wise
	 *  patpix[2048..3072-1] hold patterns flipped y-wise
	 *  patpix[3072..3072-1] hold patterns flipped x-wise and y-wise
	 */
	final private void updatepatpix() {
		if (!anydirty/* && !alldirty*/)
			return;

		for (int i = 0; i < 1024; ++i)
		{
			if (i == 384) i = 512;
			if (i == 896) break;
			if (!patdirty[i]/* && !alldirty*/) continue;
			patdirty[i] = false;

			for (int y = 0; y < 8; ++y) {
				int lineofs = (i*16) + (y*2);
				for (int x = 0; x < 8; ++x) { // not really x, but 7-x
					// this info is always in bank 0, so read directly from VRAM[0]
					int col = (VRAM[lineofs]>>x)&1;
					col |= ((VRAM[lineofs+1]>>x)&1)<<1;
					patpix[i]     [y]  [7-x] = col;
					patpix[i+1024][y]  [x]   = col;
					patpix[i+2048][7-y][7-x] = col;
					patpix[i+3072][7-y][x]   = col;
				}
			}
		}
		anydirty = false;
		//alldirty = false;
	}

	final public boolean renderNextScanline() {
		++LY;
		if (LY >= 154)
			LY = 0;

		STAT &= ~(1<<2);             // clear coincidence bit
		if (LY==LYC) {               // if equal
			STAT |= 1<<2;              // then set it
			if ((STAT&(1<<6))!=0)      // if LYC=LY is enabled in STAT reg
				cpu.triggerInterrupt(1); // request int STAT/LYC=LY
		}

		if (LY < 144) {              // HBLANK
			renderScanLine(); // renders LY
			STAT &= ~(3);              // mode=0
			if ((STAT&(1<<3))!=0)      // if HBlank is enabled in STAT reg
				cpu.triggerInterrupt(1); // request int STAT/HBlank
		}

		if (LY == 144) {             // VBLANK
			blitImage();
			if (listener != null) listener.updateUI();
			STAT &= ~(3);
			STAT |= 1;                 // mode=1
			if ((STAT&(1<<4))!=0)      // if VBlank is enabled in STAT reg
				cpu.triggerInterrupt(1); // request int STAT/VBlank
			cpu.triggerInterrupt(0);   // always request int VBLANK
		}
		return (LY == 144);
	}

	final public int read(int index) {
		if(index<0x8000) {
			System.out.println("Error: VideoController.read(): Reading from non VideoController-Address "+index);
		}
		else if(index < 0xa000) {
			return VRAM[index-0x8000+CurrentVRAMBank];
		}
		else if(index<0xfe00) {
			System.out.println("Error: VideoController.read(): Reading from non VideoController-Address "+index);
		}
		else if(index<0xfea0) {
			return OAM[index-0xfe00];
		}
		else {
			System.out.println("Error: VideoController.read(): Reading from non VideoController-Address "+index);
		}
		return VRAM[index+CurrentVRAMBank];
	}

	final public void write(int index, int value) {
		if(index<0x8000) {
			System.out.println("Error: VideoController.write(): Writing to non VideoController-Address "+index+" value="+value);
		}
		else if(index < 0xa000) {
			VRAM[index-0x8000+CurrentVRAMBank]=value;
			patdirty[(CurrentVRAMBank>>4)+((index-0x8000)>>4)] = true; // nicked from gnuboy...
			anydirty = true;
			//alldirty = true;
		}
		else if(index<0xfe00) {
			System.out.println("Error: VideoController.write(): Writing to non VideoController-Address "+index+" value="+value);
		}
		else if(index<0xfea0) {
			OAM[index-0xfe00]=value;
		}
		else {
			System.out.println("Error: VideoController.write(): Writing to non VideoController-Address "+index+" value="+value);
		}
	}

	final public void selectVRAMBank(int i) {
		CurrentVRAMBank=i*0x2000;
		if ((i <0) || (i > 1))
			System.out.printf("current offset=%x\n",CurrentVRAMBank);
	}

	final public int getcurVRAMBank() {
		return CurrentVRAMBank/0x2000;
	}

	/* rendering of scanline starts here */
	// some global vars for render procedure
	// not thread-safe, who cares!
	private static int TileData;
	private static int BGTileMap;
	private static int WindowTileMap;
	private static int bgY;
	private static int bgTileY;
	private static int bgOffsY;
	private static int bgX;
	private static int bgTileX;
	private static int bgOffsX;
	private static int windX;
	private static int tilebufBG[] = new int[0x200]; // ? max here could be lower?
	private static int blitLine[];

	final private void renderScanLine() {
		/* FF40 - LCDC - LCD Control (R/W)
		* Bit 7 - LCD Display Enable             (0=Off, 1=On)
		* Bit 6 - Window Tile Map Display Select (0=9800-9BFF, 1=9C00-9FFF)
		* Bit 5 - Window Display Enable          (0=Off, 1=On)
		* Bit 4 - BG & Window Tile Data Select   (0=8800-97FF, 1=8000-8FFF)
		* Bit 3 - BG Tile Map Display Select     (0=9800-9BFF, 1=9C00-9FFF)
		* Bit 2 - OBJ (Sprite) Size              (0=8x8, 1=8x16)
		* Bit 1 - OBJ (Sprite) Display Enable    (0=Off, 1=On)
		* Bit 0 - BG Display (for CGB see below) (0=Off, 1=On)
		*/
		if((LCDC&(1<<7))!=0) { //LCD enabled

			updatepatpix();

			blitLine = blitImg[LY];

			TileData = ((LCDC&(1<<4))==0) ? 0x0800 : 0x0000;
			BGTileMap = ((LCDC&(1<<3))==0) ? 0x1800 : 0x1c00;
			WindowTileMap = ((LCDC&(1<<6))==0) ? 0x1800 : 0x1c00;

			/* When Bit 0 is cleared, the background and window lose their priority - the sprites will be always
			 * displayed on top of background and window, independently of the priority flags in OAM and BG Map
			 * attributes.
			 */
			int BGPrio = (LCDC&(1<<0)); // ok dunno what exactly this does atm

			windX = 160;
			if(((LCDC&(1<<5))!=0)			 //window display enabled
			&& (WX >= 0) && (WX < 167) // yes this is 160+7
			&& (WY >= 0) && (WY < 144)
			&& (LY >= WY))
				windX = (WX - 7);         // [-8 < wndX < 160]

			renderScanlineBG();

			if (windX < 160) { // window doesnt have height, width
				renderScanlineWindow();
			}

			if((LCDC&(1<<1))!=0) { // sprites enabled
				renderScanlineSprites();
			}
		}
	}

	private int lbgTileY = -1;

	final private void calcBGTileBuf() {
		/*	not really 100% correct...
				if (lbgTileY==bgTileY)
			return;
		lbgTileY=bgTileY;
		*/
		int tileMap = BGTileMap + bgTileX + (bgTileY*32);
		int attrMap = tileMap + 0x2000;
		int bufMap = 0;
		int cnt = ((windX+7) >> 3) + 1;

		for (int i = 0; i < cnt; ++i) {
			int tile = VRAM[tileMap++];
			int attr = VRAM[attrMap++];
			if (TileData == 0x0800) {
				tile ^= 0x80;
				tile += 0x80;
			}
			tilebufBG[bufMap++] = tile |
			 ((attr & 0x08) << 6) |              // bank select
			 ((attr & 0x60) << 5);               // horiz/vert flip
			tilebufBG[bufMap++] = (attr&7 | 0x08) << 2; // pal select
			if ((tileMap&31)==0) { //if ((attrMap&31)==0)
				tileMap -= 32;
				attrMap -= 32;
			}
		}
	}

	final private void renderScanlineBG() {
		int bufMap = 0;
		bgY     = (SCY+LY)&0xFF;
		bgTileY = bgY >> 3;
		bgOffsY = bgY & 7;
		bgX     = SCX; // it wraps, too
		bgTileX = bgX >> 3; // tile x
		bgOffsX = bgX & 7; // x offs

		calcBGTileBuf();

		int cnt = windX;

		int PatLine[] = patpix[tilebufBG[bufMap++]][bgOffsY];
		int TilePal = tilebufBG[bufMap++];
		int curX = 0;

		for (int t = bgOffsX; t < 8; ++t, --cnt)
			//drawPixel(curX++, LY, TilePal | 0x08, patpix[TileNum][bgOffsY][t]);
			blitLine[curX++] = TilePal | PatLine[t];

		if (cnt == 0) return;

		while (cnt>=8) {
			PatLine = patpix[tilebufBG[bufMap++]][bgOffsY];
			TilePal = tilebufBG[bufMap++];
			for (int t = 0; t < 8; ++t)
				//drawPixel(curX++, LY, TilePal | 0x08, patpix[TileNum][bgOffsY][t]);
				blitLine[curX++] = TilePal | PatLine[t];
			cnt -= 8;
		}
		PatLine = patpix[tilebufBG[bufMap++]][bgOffsY];
		TilePal = tilebufBG[bufMap++];
		for (int t = 0; cnt > 0; --cnt, ++t)
			//drawPixel(curX++, LY, TilePal | 0x08, patpix[TileNum][bgOffsY][t]);
			blitLine[curX++] = TilePal | PatLine[t];
	}

	final private void renderScanlineWindow() {
		int ry  = LY - WY;
		int rty = ry >> 3; // tile y
		int rsy = ry & 7;  // y offs
		for (int x = Math.max(windX, 0); x < 160; ++x) { // [wndX <= x < 160]
			int rx = x - windX; // [-8 < wndX < 160] && [wndX <= x < 160] => [0 <= rx < 167]
			int rtx = rx >> 3; // tile xx
			int rsx = rx & 7;  // x offs

			int TileNum = VRAM[WindowTileMap + rtx + (rty*32)]; // get number of current tile
			if (TileData == 0x0800) {
				TileNum ^= 0x80; // this should do: -128 -> 0 ; 0 -> 128 ; -1 -> 127 ; 1 -> 129 ; 127 -> 255
				TileNum += 0x80;
			}

			int TileAttr = VRAM[0x2000 + WindowTileMap + rtx + (rty*32)]; // get attributes of current tile

			if ((TileAttr&(1<<3))!=0) TileNum |= (1<<9);  // bank select
			if ((TileAttr&(1<<5))!=0) TileNum |= (1<<10); // horiz flip
			if ((TileAttr&(1<<6))!=0) TileNum |= (1<<11); // vert flip

			int palnr = TileAttr & 7;

			int col = patpix[TileNum][rsy][rsx];

			drawPixel(x, LY, palnr | 0x08, col);
		}
	}

	final private void renderScanlineSprites() {
		boolean spr8x16 = ((LCDC&(1<<2))!=0);

		for (int spr = 0; spr < 40; ++spr) {
			int sprY    = OAM[(spr*4) + 0];
			int sprX    = OAM[(spr*4) + 1];
			int sprNum  = OAM[(spr*4) + 2];
			int sprAttr = OAM[(spr*4) + 3];

			int ofsY = LY - sprY + 16;

			//check if sprite is visible on this scanline
			if ((ofsY >= 0) && (ofsY < (spr8x16 ? 16 : 8))
			&&  (sprX > 0) && (sprX < 168)) {
				if ((sprAttr&(1<<6))!=0) ofsY = (spr8x16 ? 15 : 7) - ofsY;  // vert  flip
				if (spr8x16) {
					sprNum &= ~1;
					sprNum |= (ofsY >= 8) ? 1 : 0;
					ofsY &= 7;
				}

				if ((sprAttr&(1<<3))!=0) sprNum |= (1<<9);  // bank select
				if ((sprAttr&(1<<5))!=0) sprNum |= (1<<10); // horiz flip
				//if ((sprAttr&(1<<6))!=0) sprNum |= (1<<11); // vert flip
				boolean prio = ((sprAttr&(1<<7))==0);

				int palnr;
				if (isCGB)
					palnr = sprAttr & 7;
				else
					palnr = (sprAttr>>4)&1;

				int[] PatLine = patpix[sprNum][ofsY];

				for (int ofsX = 0; ofsX < 8; ++ofsX) {
					int rx = sprX - 8 + ofsX;
					// 0 is transparent color
					int col = PatLine[ofsX];
					if((col != 0) && (rx >= 0) && (rx < 160)
					&& (prio || ((blitLine[rx]&0x3c)== 0x20))) {
						drawPixel(rx, LY, palnr, col);
					}
				}
			}
		}
	}
}