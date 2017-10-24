import javax.swing.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;

import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.*;
import com.jogamp.opengl.glu.*;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.awt.ImageUtil;
import com.jogamp.opengl.util.texture.TextureIO;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

public class A4Q1 implements GLEventListener, KeyListener, MouseListener, MouseMotionListener {
	public static final boolean TRACE = false;

	public static final String WINDOW_TITLE = "A4Q1: Brendan Stothers";
	public static final int INITIAL_WIDTH = 640;
	public static final int INITIAL_HEIGHT = 640;
	
	public static final int FLOOR_SIZE = 10;
	public static final float FLOOR_EDGE = -0.5f;
	public static final float WALK_SPEED = 0.1f;
	public static final float ROTATE_SPEED = 3f;
	public static final float JUMP_HEIGHT = -2.5f;
	public static final float JUMP_INCREMENT = -0.1f;
	
	public static final int NUM_RAINDROPS = 150;
	
	public static Texture[] textures;
	public static final String[] TEXTURES = new String[] { 	"resources/textures/grass.jpg", "resources/textures/leavesbot.jpg", "resources/textures/leavesmid.jpg", "resources/textures/bark.jpg", "resources/textures/hive.jpg", "resources/textures/crate.jpg" };
	public static final int TEXTURE_GRASS = 0;
	public static final int TEXTURE_LEAVESBOT = 1;
	public static final int TEXTURE_LEAVESMID = 2;
	public static final int TEXTURE_BARK = 3;
	public static final int TEXTURE_HIVE = 4;
	public static final int TEXTURE_CRATE = 5;
	
	public static final int SKY_FRONT = 2;
	public static final int SKY_BACK = 0;
	public static final int SKY_LEFT = 3;
	public static final int SKY_RIGHT = 4;
	public static final int SKY_UP = 5;
	public static final int SKY_DOWN = 1;
	public static final String[] SKYBOX_TEXTURES = new String[] { "resources/skybox/purplenebula_bk.jpg", "resources/skybox/purplenebula_dn.jpg", "resources/skybox/purplenebula_ft.jpg",
																	"resources/skybox/purplenebula_lf.jpg", "resources/skybox/purplenebula_rt.jpg", "resources/skybox/purplenebula_up.jpg"};

	// Name of the input file path
	public static final String INPUT_PATH_NAME = "resources/";

	private static final GLU glu = new GLU();

	public static void main(String[] args) {
		final JFrame frame = new JFrame(WINDOW_TITLE);

		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (TRACE)
					System.out.println("closing window '" + ((JFrame)e.getWindow()).getTitle() + "'");
				System.exit(0);
			}
		});

		final GLProfile profile = GLProfile.get(GLProfile.GL2);
		final GLCapabilities capabilities = new GLCapabilities(profile);
		final GLCanvas canvas = new GLCanvas(capabilities);
		try {
			Object self = self().getConstructor().newInstance();
			self.getClass().getMethod("setup", new Class[] { GLCanvas.class }).invoke(self, canvas);
			canvas.addGLEventListener((GLEventListener)self);
			canvas.addKeyListener((KeyListener)self);
			canvas.addMouseListener((MouseListener)self);
			canvas.addMouseMotionListener((MouseMotionListener)self);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		canvas.setSize(INITIAL_WIDTH, INITIAL_HEIGHT);

		frame.getContentPane().add(canvas);
		frame.pack();
		frame.setVisible(true);

		canvas.requestFocusInWindow();

		if (TRACE)
			System.out.println("-> end of main().");
	}

	private static Class<?> self() {
		// This ugly hack gives us the containing class of a static method 
		return new Object() { }.getClass().getEnclosingClass();
	}

	/*** Instance variables and methods ***/
	
	private Texture[] skybox;
	
	private Robot robot;
	ArrayList<Raindrop> raindrops = new ArrayList<Raindrop>();
	ArrayList<Scenery> scenery = new ArrayList<Scenery>();
	private Scenery[][] floor = new Scenery[FLOOR_SIZE][FLOOR_SIZE];
	
	private boolean autowalk = false;
	private boolean walking = false;
	private boolean firstPerson = true;
	private boolean freelook = false;
	private boolean jumping = false;
	
	private boolean fog = true;
	
	private float modifier = JUMP_INCREMENT;
	private float currHeight = -0.1f;
	
	private int[] lastMousePos = null;
	
	private float mouseXRotation;
	private float mouseYRotation;

	public void setup(final GLCanvas canvas) {
		// Called for one-time setup
		if (TRACE)
			System.out.println("-> executing setup()");

		new Timer().scheduleAtFixedRate(new TimerTask() {
			public void run() {
				canvas.repaint();
			}
		}, 1000, 1000/60);
		
		// TODO: Add/modify code here
		
		for(int i = 0; i < FLOOR_SIZE; i++)
		{
				for(int j = 0; j < FLOOR_SIZE; j++)
				{
					floor[i][j] = new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "floor.obj", "floor")}, new float[][]{{j, 0, i}});
				}
		}
		
		for(int i = 0; i < NUM_RAINDROPS; i++)
		{
			raindrops.add(new Raindrop());
		}
		
		scenery.add(new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "crate.obj", "crate") }, new float[][]{ {1, 0.3f, 4} }));
		scenery.add(new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "crate.obj", "crate") }, new float[][]{ {6, 0.3f, 5} }));
		scenery.add(new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "crate.obj", "crate") }, new float[][]{ {4, 0.3f, 1} }));
		scenery.add(new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "crate.obj", "crate") }, new float[][]{ {8, 0.3f, 8} }));
		scenery.add(new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "trunk.obj", "trunk"), 
												new Shape(INPUT_PATH_NAME + "leavesbot.obj", "leavesbot"), 
												new Shape(INPUT_PATH_NAME + "leavesmid.obj", "leavesmid")}, 
												new float[][]{{4, 1f, 4}, {4, 1f, 4}, {4, 1f, 4}}));
		scenery.add(new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "trunk.obj", "trunk"), 
												new Shape(INPUT_PATH_NAME + "leavesbot.obj", "leavesbot"), 
												new Shape(INPUT_PATH_NAME + "leavesmid.obj", "leavesmid")}, 
												new float[][]{{8, 1f, 1}, {8, 1f, 1}, {8, 1f, 1}}));
		scenery.add(new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "trunk.obj", "trunk"), 
												new Shape(INPUT_PATH_NAME + "leavesbot.obj", "leavesbot"), 
												new Shape(INPUT_PATH_NAME + "leavesmid.obj", "leavesmid")}, 
												new float[][]{{2, 1f, 8}, {2, 1f, 8}, {2, 1f, 8}}));
		scenery.add(new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "hive.obj", "hive") }, new float[][]{ {3, 0.85f, 3.5f} }));
		scenery.add(new Scenery(new Shape[] { new Shape(INPUT_PATH_NAME + "hive.obj", "hive") }, new float[][]{ {3f, 0.85f, 7.5f} }));
		
		robot = new Robot(new Shape[] { new Shape(INPUT_PATH_NAME + "head.obj", "head"), 
				new Shape(INPUT_PATH_NAME + "body.obj", "body"), 
				new Shape(INPUT_PATH_NAME + "upperleftarm.obj", "upperleftarm"), 
				new Shape(INPUT_PATH_NAME + "lowerleftarm.obj", "lowerleftarm"), 
				new Shape(INPUT_PATH_NAME + "upperrightarm.obj", "upperrightarm"), 
				new Shape(INPUT_PATH_NAME + "lowerrightarm.obj", "lowerrightarm"), 
				new Shape(INPUT_PATH_NAME + "upperleftleg.obj", "upperleftleg"), 
				new Shape(INPUT_PATH_NAME + "lowerleftleg.obj", "lowerleftleg"), 
				new Shape(INPUT_PATH_NAME + "upperrightleg.obj", "upperrightleg"), 
				new Shape(INPUT_PATH_NAME + "lowerrightleg.obj", "lowerrightleg")}, 
				new float[][] {{0.0f, 123.0f, -19.0f}, {0.0f, 0.75f, 0.0f}, {0.0f, 0.75f, 0.0f}, {0.0f, 0.75f, 0.0f}, {0.0f, 0.75f, 0.0f}, {0.0f, 0.75f, 0.0f}, {0.0f, 0.75f, 0.0f}, {0.0f, 0.75f, 0.0f}, {0.0f, 0.75f, 0.0f}, {0.0f, 0.75f, 0.0f}});
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		// Called when the canvas is (re-)created - use it for initial GL setup
		if (TRACE)
			System.out.println("-> executing init()");

		final GL2 gl = drawable.getGL().getGL2();
		
		skybox = new Texture[SKYBOX_TEXTURES.length];
		textures = new Texture[TEXTURES.length];
		try 
		{
			for (int i = 0; i < TEXTURES.length; i++) 
			{
				File infile = new File(TEXTURES[i]);
				BufferedImage image = ImageIO.read(infile);
				ImageUtil.flipImageVertically(image);
				textures[i] = TextureIO.newTexture(AWTTextureIO.newTextureData(gl.getGLProfile(), image, true));
				
				textures[i].setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT); // GL_REPEAT or GL_CLAMP
				textures[i].setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
				textures[i].setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
				textures[i].setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
			}
			
			for (int i = 0; i < SKYBOX_TEXTURES.length; i++) 
			{
				File infile = new File(SKYBOX_TEXTURES[i]);
				BufferedImage image = ImageIO.read(infile);
				ImageUtil.flipImageVertically(image);
				skybox[i] = TextureIO.newTexture(AWTTextureIO.newTextureData(gl.getGLProfile(), image, true));
		
				skybox[i].setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
				skybox[i].setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_NEAREST);
			}
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
		// fog
		gl.glEnable(GL2.GL_FOG);
		gl.glFogi(GL2.GL_FOG_MODE, GL2.GL_LINEAR);
		gl.glFogf(GL2.GL_FOG_START, 2f);
		gl.glFogf(GL2.GL_FOG_END, 6f);

		// TODO: Add code here
		gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glDepthFunc(GL2.GL_LEQUAL);
		gl.glEnable(GL2.GL_BLEND);
		gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		// Draws the display
		if (TRACE)
			System.out.println("-> executing display()");

		final GL2 gl = drawable.getGL().getGL2();

		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
		
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		
		gl.glFrustumf(-1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 15.0f);

		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		if(autowalk)
		{
			if(!sceneryCollision(robot.testNextPosition(WALK_SPEED / 2f)))
			{
				robot.updatePosition(WALK_SPEED / 2f);
			}
		}
		
		if(fog)
		{
			gl.glEnable(GL2.GL_FOG); 
		}
		else
		{
			gl.glDisable(GL2.GL_FOG);
		}
		
		if(firstPerson && freelook)
		{
			gl.glTranslatef(robot.xPos, -1.5f, robot.zPos);
			gl.glTranslatef(-robot.xPos, -robot.yPos, -robot.zPos);
			gl.glRotatef(-mouseYRotation, 1.0f, 0.0f, 0.0f);
			gl.glRotatef(mouseXRotation, 0.0f, 1.0f, 0.0f);
			gl.glRotatef(-robot.yRotation, 0.0f, 1.0f, 0.0f);
			gl.glTranslatef(robot.xPos, robot.yPos, robot.zPos);
			gl.glRotatef(180f, 0.0f, 1.0f, 0.0f);
		}
		else if(!firstPerson && freelook)
		{
			gl.glTranslatef(robot.xPos, -2f, robot.zPos - 2.5f);
			gl.glTranslatef(-robot.xPos, -robot.yPos, -robot.zPos);
			gl.glRotatef(-mouseYRotation, 1.0f, 0.0f, 0.0f);
			gl.glRotatef(mouseXRotation, 0.0f, 1.0f, 0.0f);
			gl.glRotatef(-robot.yRotation, 0.0f, 1.0f, 0.0f);
			gl.glTranslatef(robot.xPos, robot.yPos, robot.zPos);
			gl.glRotatef(180f, 0.0f, 1.0f, 0.0f);
		}
		else if(firstPerson)
		{			
			if(jumping)
			{
				if(!sceneryCollision(robot.testNextPosition(WALK_SPEED / 2f)) || robot.yPos > JUMP_HEIGHT / 2f)
				{
					robot.updatePosition(WALK_SPEED / 2f);
				}
				
				gl.glTranslatef(robot.xPos, -1.5f + (currHeight += modifier), robot.zPos);
				gl.glTranslatef(-robot.xPos, -robot.yPos, -robot.zPos);
				gl.glRotatef(-robot.yRotation, 0.0f, 1.0f, 0.0f);
				gl.glTranslatef(robot.xPos, robot.yPos, robot.zPos);
				gl.glRotatef(180f, 0.0f, 1.0f, 0.0f);
				
				if(currHeight <= JUMP_HEIGHT)
				{	
					modifier *= -1;
				}
				
				if(currHeight >= 0.0f)
				{
					jumping = false;
					currHeight = -0.1f;
					modifier *= -1;
				}
			}
			else
			{
				gl.glTranslatef(robot.xPos, -1.5f, robot.zPos);
				gl.glTranslatef(-robot.xPos, -robot.yPos, -robot.zPos);
				gl.glRotatef(-robot.yRotation, 0.0f, 1.0f, 0.0f);
				gl.glTranslatef(robot.xPos, robot.yPos, robot.zPos);
				gl.glRotatef(180f, 0.0f, 1.0f, 0.0f);
			}
		}
		else
		{	
			if(jumping)
			{
				if(!sceneryCollision(robot.testNextPosition(WALK_SPEED / 2f)) || robot.yPos > JUMP_HEIGHT / 2f)
				{
					robot.updatePosition(WALK_SPEED / 2f);
				}
				
				gl.glTranslatef(robot.xPos, -2f + (currHeight += modifier), robot.zPos - 2.5f);
				gl.glTranslatef(-robot.xPos, -robot.yPos, -robot.zPos);
				gl.glRotatef(-robot.yRotation, 0.0f, 1.0f, 0.0f);
				gl.glTranslatef(robot.xPos, robot.yPos, robot.zPos);
				gl.glRotatef(180f, 0.0f, 1.0f, 0.0f);
				
				robot.updateYPosition(-1 * modifier); // value has to go up instead of down
				
				if(currHeight <= JUMP_HEIGHT)
				{	
					modifier *= -1;
				}
				
				if(currHeight >= 0.0f)
				{
					jumping = false;
					robot.setYPosition(0.0f);
					currHeight = -0.1f;
					modifier *= -1;
				}
			}
			else
			{
				gl.glTranslatef(robot.xPos, -2f, robot.zPos - 2.5f);
				gl.glTranslatef(-robot.xPos, -robot.yPos, -robot.zPos);
				gl.glRotatef(-robot.yRotation, 0.0f, 1.0f, 0.0f);
				gl.glTranslatef(robot.xPos, robot.yPos, robot.zPos);
				gl.glRotatef(180f, 0.0f, 1.0f, 0.0f);
			}
		}
		
		if(!firstPerson)
		{
			robot.draw(gl, walking);
		}
		
		if(edgeCollision())
		{
			robot.xPos = 0.0f;
			robot.zPos = 0.0f;
		}
		
		robot.updateBoundingBox();
		//robot.drawBoundingBox(gl);
		
		drawFloor(gl);
		drawScenery(gl);
		drawRaindrops(gl);
		drawSkybox(gl, robot.xPos, robot.yPos, robot.zPos, 10f, 10f, 10f);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		// Called when the canvas is destroyed (reverse anything from init) 
		if (TRACE)
			System.out.println("-> executing dispose()");
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		// Called when the canvas has been resized
		// Note: glViewport(x, y, width, height) has already been called so don't bother if that's what you want
		if (TRACE)
			System.out.println("-> executing reshape(" + x + ", " + y + ", " + width + ", " + height + ")");

		final GL2 gl = drawable.getGL().getGL2();
	}
	
	@Override
	public void mouseExited(MouseEvent e) 
	{
	}

	@Override
	public void mouseEntered(MouseEvent e) 
	{
	}
	
	@Override
	public void mouseMoved(MouseEvent event) 
	{
	}
	
	@Override
	public void mouseClicked(MouseEvent e) 
	{
	}
	
	@Override
	public void mousePressed(MouseEvent e) 
	{
		int x = e.getX();
		int y = ((GLCanvas)(e.getSource())).getHeight() - e.getY() - 1;
		
		lastMousePos = new int[] {x, y};
		freelook = true;
	}
	
	@Override
	public void mouseDragged(MouseEvent event) 
	{
		int x = event.getX();
		int y = ((GLCanvas)(event.getSource())).getHeight() - event.getY() - 1;
		
		if(lastMousePos != null)
		{
			int deltaX = x - lastMousePos[0];
			int deltaY = y - lastMousePos[1];
			
			mouseXRotation = deltaX / 10f;
			mouseYRotation = deltaY / 10f;
		}
	}
	
	@Override
	public void mouseReleased(MouseEvent e) 
	{
		freelook = false;
	}

	@Override
	public void keyPressed(KeyEvent e) 
	{
		if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyChar() == 'a')
		{
			robot.updateYRotation(ROTATE_SPEED);
		}
		
		if (e.getKeyCode() == KeyEvent.VK_RIGHT || e.getKeyChar() == 'd')
		{
			robot.updateYRotation(-ROTATE_SPEED);
		}
		
		if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyChar() == 'w')
		{	
			if(!sceneryCollision(robot.testNextPosition(WALK_SPEED)))
			{
				robot.updatePosition(WALK_SPEED);
			}
		}
		
		if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyChar() == 's')
		{
			if(!sceneryCollision(robot.testNextPosition(-WALK_SPEED)))
			{
				robot.updatePosition(-WALK_SPEED);
			}
		}
		
		if (e.getKeyChar() == ' ') 
		{
			System.out.println("jumping!");
			jumping = true;
		} 
		else if (e.getKeyChar() == '\n') 
		{
			System.out.println("Enter: switch view");
			firstPerson = !firstPerson;
		}
	}

	@Override
	public void keyReleased(KeyEvent e) 
	{	
		if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyChar() == 'w')
		{
			walking = false;
		}
		
		if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyChar() == 's')
		{
			walking = false;
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub
		
		if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyChar() == 'w')
		{
			walking = true;
		}
		
		if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyChar() == 's')
		{
			walking = true;
		}
		
		if (e.getKeyChar() == 'q')
		{
			autowalk = !autowalk;
			walking = !walking;
			
			System.out.println("Autowalk: " + autowalk);
		}
		
		if (e.getKeyChar() == 'e')
		{
			fog = !fog;
			System.out.println("Fog: " + fog);
		}
	}
	
	public void drawFloor(GL2 gl)
	{	
		for(int i = 0; i < floor.length; i++)
		{
			for(int j = 0; j < floor[i].length; j++)
			{
				if(floor[i][j] != null)
				{
					floor[i][j].draw(gl);
				}
			}
		}
	}
	
	public void drawScenery(GL2 gl)
	{	
		for(int i = 0; i < scenery.size(); i++)
		{
			scenery.get(i).draw(gl);
			//scenery.get(i).drawBoundingBox(gl);;
		}
	}
	
	public void drawRaindrops(GL2 gl)
	{
		for(int i = raindrops.size() - 1; i >= 0; i--)
		{
			Raindrop curr = raindrops.get(i);
			
			if(curr.isGrounded())
			{
				raindrops.remove(i);
				raindrops.add(new Raindrop());
			}
			else
			{
				curr.draw(gl);
			}
		}
	}
	
	public void drawSkybox(GL2 gl, float x, float y, float z, float width, float height, float length)
	{
		x = x - (width / 2);
		y = y - (height / 2);
		z = z - (length / 2);
		
		gl.glColor3f(1f, 1f, 1f);
		skybox[SKY_FRONT].enable(gl);
		skybox[SKY_FRONT].bind(gl);
		gl.glBegin(GL2.GL_QUADS);	
		gl.glTexCoord2f(1.0f, 0.0f); 
		gl.glVertex3f(x, y, z + length);
		gl.glTexCoord2f(1.0f, 1.0f);
		gl.glVertex3f(x, y + height, z + length);
		gl.glTexCoord2f(0.0f, 1.0f); 
		gl.glVertex3f(x + width, y + height, z + length); 
		gl.glTexCoord2f(0.0f, 0.0f); 
		gl.glVertex3f(x + width, y, z + length);
		gl.glEnd();
		skybox[SKY_FRONT].disable(gl);
		
		skybox[SKY_BACK].enable(gl);
		skybox[SKY_BACK].bind(gl);
		gl.glBegin(GL2.GL_QUADS);	
		gl.glTexCoord2f(1.0f, 0.0f); 
		gl.glVertex3f(x + width, y, z);
		gl.glTexCoord2f(1.0f, 1.0f);
		gl.glVertex3f(x + width, y + height, z);
		gl.glTexCoord2f(0.0f, 1.0f); 
		gl.glVertex3f(x, y + height, z); 
		gl.glTexCoord2f(0.0f, 0.0f); 
		gl.glVertex3f(x, y, z);
		gl.glEnd();
		skybox[SKY_FRONT].disable(gl);
		
		skybox[SKY_LEFT].enable(gl);
		skybox[SKY_LEFT].bind(gl);
		gl.glBegin(GL2.GL_QUADS);	
		gl.glTexCoord2f(1.0f, 0.0f); 
		gl.glVertex3f(x, y + height, z);
		gl.glTexCoord2f(1.0f, 1.0f);
		gl.glVertex3f(x, y + height, z + length);
		gl.glTexCoord2f(0.0f, 1.0f); 
		gl.glVertex3f(x, y, z + length); 
		gl.glTexCoord2f(0.0f, 0.0f); 
		gl.glVertex3f(x, y, z);
		gl.glEnd();
		skybox[SKY_LEFT].disable(gl);
		
		skybox[SKY_RIGHT].enable(gl);
		skybox[SKY_RIGHT].bind(gl);
		gl.glBegin(GL2.GL_QUADS);	
		gl.glTexCoord2f(1.0f, 0.0f); 
		gl.glVertex3f(x + width, y, z);
		gl.glTexCoord2f(1.0f, 1.0f);
		gl.glVertex3f(x + width, y, z + length);
		gl.glTexCoord2f(0.0f, 1.0f); 
		gl.glVertex3f(x + width, y + height, z + length); 
		gl.glTexCoord2f(0.0f, 0.0f); 
		gl.glVertex3f(x + width, y + height, z);
		gl.glEnd();
		skybox[SKY_RIGHT].disable(gl);
		
		skybox[SKY_UP].enable(gl);
		skybox[SKY_UP].bind(gl);
		gl.glBegin(GL2.GL_QUADS);	
		gl.glTexCoord2f(1.0f, 0.0f); 
		gl.glVertex3f(x + width, y + height, z);
		gl.glTexCoord2f(1.0f, 1.0f);
		gl.glVertex3f(x + width, y + height, z + length);
		gl.glTexCoord2f(0.0f, 1.0f); 
		gl.glVertex3f(x, y + height, z + length); 
		gl.glTexCoord2f(0.0f, 0.0f); 
		gl.glVertex3f(x, y + height, z);
		gl.glEnd();
		skybox[SKY_UP].disable(gl);
		
		skybox[SKY_DOWN].enable(gl);
		skybox[SKY_DOWN].bind(gl);
		gl.glBegin(GL2.GL_QUADS);	
		gl.glTexCoord2f(1.0f, 0.0f); 
		gl.glVertex3f(x, y, z);
		gl.glTexCoord2f(1.0f, 1.0f);
		gl.glVertex3f(x, y, z + length);
		gl.glTexCoord2f(0.0f, 1.0f); 
		gl.glVertex3f(x + width, y, z + length); 
		gl.glTexCoord2f(0.0f, 0.0f); 
		gl.glVertex3f(x + width, y, z);
		gl.glEnd();
		skybox[SKY_DOWN].disable(gl);
	}
	
	public boolean sceneryCollision(float[][] test)
	{
		boolean collision = false;
		
		for(int i = 0; i < scenery.size() && !collision; i++)
		{
			collision = scenery.get(i).hitTest(test);
		}
			
		return collision;
	}
	
	public boolean edgeCollision()
	{
		return (robot.zPos > FLOOR_SIZE + FLOOR_EDGE || robot.zPos < FLOOR_EDGE || robot.xPos > FLOOR_SIZE + FLOOR_EDGE || robot.xPos < FLOOR_EDGE);
	}
}

class Face {
	private int[] indices;
	private float[] colour;
	
	public Face(int[] indices, float[] colour) {
		this.indices = new int[indices.length];
		this.colour = new float[colour.length];
		System.arraycopy(indices, 0, this.indices, 0, indices.length);
		System.arraycopy(colour, 0, this.colour, 0, colour.length);
	}
	
	public void draw(GL2 gl, ArrayList<float[]> vertices, boolean useColour, String part) 
	{
		if (useColour) 
		{
			if (colour.length == 3)
				gl.glColor3f(colour[0], colour[1], colour[2]);
			else
				gl.glColor4f(colour[0], colour[1], colour[2], colour[3]);
		}
		
		if(part.equals("floor"))
		{	
			gl.glColor3f(1f, 1f, 1f);
			A4Q1.textures[A4Q1.TEXTURE_GRASS].enable(gl);
			A4Q1.textures[A4Q1.TEXTURE_GRASS].bind(gl);
			gl.glBegin(GL2.GL_QUADS);
			
			for(int i = 0; i < indices.length; i++)
			{
				if(i == 0)
				{
					gl.glTexCoord2f(0f, 1f);
				}
				else if(i == 1)
				{
					gl.glTexCoord2f(1f, 1f);
				}
				else if(i == 2)
				{
					gl.glTexCoord2f(1f, 0f);
				}
				else if(i == 3)
				{
					gl.glTexCoord2f(0f, 0f);
				}
				
				gl.glVertex3f(vertices.get(indices[i])[0], vertices.get(indices[i])[1], vertices.get(indices[i])[2]);
			}
			
			gl.glEnd();
			A4Q1.textures[A4Q1.TEXTURE_GRASS].disable(gl);
		}
		else if(part.equals("leavesbot"))
		{
			gl.glColor3f(1f, 1f, 1f);
			A4Q1.textures[A4Q1.TEXTURE_LEAVESBOT].enable(gl);
			A4Q1.textures[A4Q1.TEXTURE_LEAVESBOT].bind(gl);
			gl.glBegin(GL2.GL_QUADS);
			
			for(int i = 0; i < indices.length; i++)
			{
				if(i == 0)
				{
					gl.glTexCoord2f(0f, 1f);
				}
				else if(i == 1)
				{
					gl.glTexCoord2f(1f, 1f);
				}
				else if(i == 2)
				{
					gl.glTexCoord2f(1f, 0f);
				}
				else if(i == 3)
				{
					gl.glTexCoord2f(0f, 0f);
				}
				
				gl.glVertex3f(vertices.get(indices[i])[0], vertices.get(indices[i])[1], vertices.get(indices[i])[2]);
			}
			
			gl.glEnd();
			A4Q1.textures[A4Q1.TEXTURE_LEAVESBOT].disable(gl);
		}
		else if(part.equals("leavesmid"))
		{
			gl.glColor3f(1f, 1f, 1f);
			A4Q1.textures[A4Q1.TEXTURE_LEAVESMID].enable(gl);
			A4Q1.textures[A4Q1.TEXTURE_LEAVESMID].bind(gl);
			gl.glBegin(GL2.GL_QUADS);
			
			for(int i = 0; i < indices.length; i++)
			{
				if(i == 0)
				{
					gl.glTexCoord2f(0f, 1f);
				}
				else if(i == 1)
				{
					gl.glTexCoord2f(1f, 1f);
				}
				else if(i == 2)
				{
					gl.glTexCoord2f(1f, 0f);
				}
				else if(i == 3)
				{
					gl.glTexCoord2f(0f, 0f);
				}
				
				gl.glVertex3f(vertices.get(indices[i])[0], vertices.get(indices[i])[1], vertices.get(indices[i])[2]);
			}
			
			gl.glEnd();
			A4Q1.textures[A4Q1.TEXTURE_LEAVESMID].disable(gl);
		}
		else if(part.equals("trunk"))
		{
			gl.glColor3f(1f, 1f, 1f);
			A4Q1.textures[A4Q1.TEXTURE_BARK].enable(gl);
			A4Q1.textures[A4Q1.TEXTURE_BARK].bind(gl);
			gl.glBegin(GL2.GL_QUADS);
			
			for(int i = 0; i < indices.length; i++)
			{
				if(i == 0)
				{
					gl.glTexCoord2f(0f, 1f);
				}
				else if(i == 1)
				{
					gl.glTexCoord2f(1f, 1f);
				}
				else if(i == 2)
				{
					gl.glTexCoord2f(1f, 0f);
				}
				else if(i == 3)
				{
					gl.glTexCoord2f(0f, 0f);
				}
				
				gl.glVertex3f(vertices.get(indices[i])[0], vertices.get(indices[i])[1], vertices.get(indices[i])[2]);
			}
			
			gl.glEnd();
			A4Q1.textures[A4Q1.TEXTURE_BARK].disable(gl);
		}
		else if(part.equals("crate"))
		{
			gl.glColor3f(1f, 1f, 1f);
			A4Q1.textures[A4Q1.TEXTURE_CRATE].enable(gl);
			A4Q1.textures[A4Q1.TEXTURE_CRATE].bind(gl);
			gl.glBegin(GL2.GL_QUADS);
			
			for(int i = 0; i < indices.length; i++)
			{
				if(i == 0)
				{
					gl.glTexCoord2f(0f, 1f);
				}
				else if(i == 1)
				{
					gl.glTexCoord2f(1f, 1f);
				}
				else if(i == 2)
				{
					gl.glTexCoord2f(1f, 0f);
				}
				else if(i == 3)
				{
					gl.glTexCoord2f(0f, 0f);
				}
				
				gl.glVertex3f(vertices.get(indices[i])[0], vertices.get(indices[i])[1], vertices.get(indices[i])[2]);
			}
			
			gl.glEnd();
			A4Q1.textures[A4Q1.TEXTURE_CRATE].disable(gl);
		}
		else if(part.equals("hive"))
		{
			gl.glColor3f(1f, 1f, 1f);
			A4Q1.textures[A4Q1.TEXTURE_HIVE].enable(gl);
			A4Q1.textures[A4Q1.TEXTURE_HIVE].bind(gl);
			gl.glBegin(GL2.GL_TRIANGLES);
			
			for(int i = 0; i < indices.length; i++)
			{
				if(i == 0)
				{
					gl.glTexCoord2f(0f, 0f);
				}
				else if(i == 1)
				{
					gl.glTexCoord2f(1f, 0f);
				}
				else if(i == 2)
				{
					gl.glTexCoord2f(1f, 1f);
				}
				
				gl.glVertex3f(vertices.get(indices[i])[0], vertices.get(indices[i])[1], vertices.get(indices[i])[2]);
			}
			
			gl.glEnd();
			A4Q1.textures[A4Q1.TEXTURE_HIVE].disable(gl);
		}
		else
		{
			if (indices.length == 1) {
				gl.glBegin(GL2.GL_POINTS);
			} else if (indices.length == 2) {
				gl.glBegin(GL2.GL_LINES);
			} else if (indices.length == 3) {
				gl.glBegin(GL2.GL_TRIANGLES);
			} else if (indices.length == 4) {
				gl.glBegin(GL2.GL_QUADS);
			} else {
				gl.glBegin(GL2.GL_POLYGON);
			}
			
			for (int i = 0; i < indices.length; i++) 
			{
				gl.glVertex3f(vertices.get(indices[i])[0], vertices.get(indices[i])[1], vertices.get(indices[i])[2]);
			}
			
			gl.glEnd();
		}
	}
}

// TODO: rewrite the following as you like
class Shape {
	// set this to NULL if you don't want outlines
	public float[] line_colour;
	public String part;
	public float[] joint;

	protected ArrayList<float[]> vertices;
	protected ArrayList<Face> faces;

	public Shape() {
		// you could subclass Shape and override this with your own
		init();
		
		// default shape: cube
	}

	public Shape(String filename, String part) {
		init();
		
		// TODO Use as you like
		// NOTE that there is limited error checking, to make this as flexible as possible
		BufferedReader input;
		String line;
		String[] tokens;

		float[] vertex;
		float[] colour;
		String specifyingMaterial = null;
		String selectedMaterial;
		int[] face;
		
		HashMap<String, float[]> materials = new HashMap<String, float[]>();
		materials.put("default", new float[] {1,1,1});
		selectedMaterial = "default";
		
		// vertex positions start at 1
		vertices.add(new float[] {0,0,0});
		
		int currentColourIndex = 0;

		// these are for error checking (which you don't need to do)
		int lineCount = 0;
		int vertexCount = 0, colourCount = 0, faceCount = 0;

		try {
			input = new BufferedReader(new FileReader(filename));

			line = input.readLine();
			while (line != null) {
				lineCount++;
				tokens = line.split("\\s+");

				if (tokens[0].equals("v")) {
					assert tokens.length == 4 : "Invalid vertex specification (line " + lineCount + "): " + line;

					vertex = new float[3];
					try {
						vertex[0] = Float.parseFloat(tokens[1]);
						vertex[1] = Float.parseFloat(tokens[2]);
						vertex[2] = Float.parseFloat(tokens[3]);
					} catch (NumberFormatException nfe) {
						assert false : "Invalid vertex coordinate (line " + lineCount + "): " + line;
					}

					//System.out.printf("vertex %d: (%f, %f, %f)\n", vertexCount + 1, vertex[0], vertex[1], vertex[2]);
					vertices.add(vertex);

					vertexCount++;
				} else if (tokens[0].equals("newmtl")) {
					assert tokens.length == 2 : "Invalid material name (line " + lineCount + "): " + line;
					specifyingMaterial = tokens[1];
				} else if (tokens[0].equals("Kd")) {
					assert tokens.length == 4 : "Invalid colour specification (line " + lineCount + "): " + line;
					assert faceCount == 0 && currentColourIndex == 0 : "Unexpected (late) colour (line " + lineCount + "): " + line;

					colour = new float[3];
					try {
						colour[0] = Float.parseFloat(tokens[1]);
						colour[1] = Float.parseFloat(tokens[2]);
						colour[2] = Float.parseFloat(tokens[3]);
					} catch (NumberFormatException nfe) {
						assert false : "Invalid colour value (line " + lineCount + "): " + line;
					}
					for (float colourValue: colour) {
						assert colourValue >= 0.0f && colourValue <= 1.0f : "Colour value out of range (line " + lineCount + "): " + line;
					}

					if (specifyingMaterial == null) {
						//System.out.printf("Error: no material name for colour %d: (%f %f %f)\n", colourCount + 1, colour[0], colour[1], colour[2]);
					} else {
						//System.out.printf("material %s: (%f %f %f)\n", specifyingMaterial, colour[0], colour[1], colour[2]);
						materials.put(specifyingMaterial, colour);
					}

					colourCount++;
				} else if (tokens[0].equals("usemtl")) {
					assert tokens.length == 2 : "Invalid material selection (line " + lineCount + "): " + line;

					selectedMaterial = tokens[1];
				} else if (tokens[0].equals("f")) {
					assert tokens.length > 1 : "Invalid face specification (line " + lineCount + "): " + line;

					face = new int[tokens.length - 1];
					try {
						for (int i = 1; i < tokens.length; i++) {
							face[i - 1] = Integer.parseInt(tokens[i].split("/")[0]);
						}
					} catch (NumberFormatException nfe) {
						assert false : "Invalid vertex index (line " + lineCount + "): " + line;
					}

					//System.out.printf("face %d: [ ", faceCount + 1);
					for (int index: face) {
						//System.out.printf("%d ", index);
					}
					//System.out.printf("] using material %s\n", selectedMaterial);
					
					colour = materials.get(selectedMaterial);
					if (colour == null) {
						System.out.println("Error: material " + selectedMaterial + " not found, using default.");
						colour = materials.get("default");
					}
					faces.add(new Face(face, colour));

					faceCount++;
				} else {
					//System.out.println("Ignoring: " + line);
				}

				line = input.readLine();
			}
		} catch (IOException ioe) {
			System.out.println(ioe.getMessage());
			assert false : "Error reading input file " + filename;
		}
		
		this.part = part;
		
		if(vertices.size() >= 2)
		{
			this.joint = new float[]{(vertices.get(1)[0] + vertices.get(2)[0]) / 2f, vertices.get(1)[1], 0.0f}; // I ordered my obj files vertices for the top left and right to be first
																												// I get the middle of the two and the middle of the z values will always be 0
		}
	}

	protected void init() {
		vertices = new ArrayList<float[]>();
		faces = new ArrayList<Face>();
		
		line_colour = new float[] { 1.000f, 0.412f, 0.706f };
	}
	
	public void draw(GL2 gl) {
		for (Face f: faces) {
			if (line_colour == null) {
				f.draw(gl, vertices, true, part);
			} else {
				gl.glEnable(GL2.GL_POLYGON_OFFSET_FILL);
				gl.glPolygonOffset(1.0f, 1.0f);
				f.draw(gl, vertices, true, part);
				gl.glDisable(GL2.GL_POLYGON_OFFSET_FILL);
				
				if(this.part != "head" && this.part != "raindrop")
				{
					gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_LINE);
					gl.glLineWidth(2.0f);
					gl.glColor3f(line_colour[0], line_colour[1], line_colour[2]);
					f.draw(gl, vertices, false, part);
					gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_FILL);
				}
			}
		}
	}
}

class Robot extends Shape {
	// this array can include other structures...
	private Shape[] contents;
	private float[][] positions;
	public float xRotation, yRotation;
	public float xPos, yPos, zPos;
	
	public float[][] boundingBox;
	public final float boxLength = 0.35f;
	
	public float leftArmRotation, leftArmModifier;
	public float rightArmRotation, rightArmModifier;
	public float leftLegRotation, leftLegModifier;
	public float rightLegRotation, rightLegModifier;
	
	// the degrees of rotation a limb is allowed to do
	private final float rightArmLimit = 20f;
	private final float leftArmLimit= 20f;
	private final float rightLegLimit = 10f;
	private final float leftLegLimit = 10f;
	
	public Robot(Shape[] contents, float[][] positions) {
		super();
		init(contents, positions);
		this.xRotation = 1.0f;
		this.yRotation = 0.0f;
		this.xPos = 0.0f;
		this.yPos = 0.0f;
		this.zPos = 0.0f;
		// left arm
		this.leftArmRotation = leftArmLimit;
		this.leftArmModifier = -1;
		// right arm
		this.rightArmRotation = -rightArmLimit; // negative to go proper direction
		this.rightArmModifier = 1;
		// left leg
		this.leftLegRotation = leftLegLimit;
		this.leftLegModifier = -0.5f;
		// right leg
		this.rightLegRotation = -rightLegLimit;
		this.rightLegModifier = 0.5f;
		updateBoundingBox();
	}
	
	private void init(Shape[] contents, float[][] positions) {
		this.contents = new Shape[contents.length];
		this.positions = new float[positions.length][3];
		System.arraycopy(contents, 0, this.contents, 0, contents.length);
		for (int i = 0; i < positions.length; i++) {
			System.arraycopy(positions[i], 0, this.positions[i], 0, 3);
		}
	}
	
	public boolean hitTest(float[][] bBox)
	{
		boolean inside = false;
		
		for(int i = 0; i < bBox.length && !inside; i++)
		{
			for(int j = 0; j < this.boundingBox.length && !inside; j++)
			{
				inside = (this.boundingBox[j][0] >= bBox[0][0] && this.boundingBox[j][0] <= bBox[1][0] && this.boundingBox[j][1] >= bBox[2][1] && this.boundingBox[j][1] <= bBox[0][1]);
			}
		}
		
		return inside;
	}
	
	public void updateBoundingBox()
	{
		this.boundingBox = new float[][]{	{this.xPos - (this.boxLength / 2f), this.zPos + (this.boxLength / 2f)}, 
											{this.xPos + (this.boxLength / 2f), this.zPos + (this.boxLength / 2f)}, 
											{this.xPos + (this.boxLength / 2f), this.zPos - (this.boxLength / 2f)}, 
											{this.xPos - (this.boxLength / 2f), this.zPos - (this.boxLength / 2f)} };
	}
	
	public void drawBoundingBox(GL2 gl)
	{
		gl.glColor3f(0f, 0f, 0f);
		gl.glPointSize(5f);
		gl.glBegin(GL2.GL_POINTS);
		gl.glVertex3f(this.boundingBox[0][0], 0.0f, this.boundingBox[0][1]);
		gl.glVertex3f(this.boundingBox[1][0], 0.0f, this.boundingBox[1][1]);
		gl.glVertex3f(this.boundingBox[2][0], 0.0f, this.boundingBox[2][1]);
		gl.glVertex3f(this.boundingBox[3][0], 0.0f, this.boundingBox[3][1]);
		gl.glEnd();
	}
	
	public float[] getHeadPosition()
	{
		return this.positions[0];
	}
	
	public void updatePosition(float speed)
	{
		float pitch = this.xRotation * (float)(Math.PI / 180);
		float yaw = this.yRotation * (float)(Math.PI / 180);
		
		this.xPos += speed * (float)Math.sin(yaw) * (float)Math.cos(pitch);
		//this.yPos += speed * -(float)Math.sin(pitch);
		this.zPos += speed * (float)Math.cos(yaw) * (float)Math.cos(pitch);
	}
	
	public float[][] testNextPosition(float speed)
	{
		float pitch = this.xRotation * (float)(Math.PI / 180);
		float yaw = this.yRotation * (float)(Math.PI / 180);	
		float testX = this.xPos + speed * (float)Math.sin(yaw) * (float)Math.cos(pitch);
		float testZ = this.zPos + speed * (float)Math.cos(yaw) * (float)Math.cos(pitch);
		
		return new float[][]{	{testX - 0.05f - (this.boxLength / 2f), testZ + (this.boxLength / 2f)}, 
			{testX + 0.05f + (this.boxLength / 2f), testZ + (this.boxLength / 2f)}, 
			{testX + 0.05f + (this.boxLength / 2f), testZ - (this.boxLength / 2f)}, 
			{testX - 0.05f - (this.boxLength / 2f), testZ - (this.boxLength / 2f)} };
	}
	
	public void updateYPosition(float update)
	{
		this.yPos += update;
	}
	
	public void setYPosition(float yPos)
	{
		this.yPos = yPos;
	}
	
	public void updateXRotation(float rotate)
	{
		this.xRotation += rotate;
	}
	
	public void updateYRotation(float rotate)
	{
		this.yRotation += rotate;
	}

	public void draw(GL2 gl, boolean walking) 
	{
		super.draw(gl);
		
		for (int i = 0; i < contents.length; i++) {
			if(!walking)
			{
				if(contents[i].part.equals("head"))
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glScalef(0.01f, 0.01f, 0.01f);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
				}
				else
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
				}
			}
			else
			{	
				if(contents[i].part.equals("head"))
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glScalef(0.01f, 0.01f, 0.01f);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
				}
				else if(contents[i].part.equals("body"))
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
				}
				else if(contents[i].part.equals("upperleftarm")) // left arm
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					gl.glTranslatef(contents[i].joint[0], contents[i].joint[1], contents[i].joint[2]);
					gl.glRotatef(this.leftArmRotation, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i].joint[0], -contents[i].joint[1], -contents[i].joint[2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
					
					this.leftArmRotation += leftArmModifier;
					
					if(this.leftArmRotation == -leftArmLimit)
					{
						leftArmModifier *= -1;
					}
					else if(this.leftArmRotation == leftArmLimit)
					{
						leftArmModifier *= -1;
					}
				}
				else if(contents[i].part.equals("lowerleftarm")) // end left arm
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					gl.glTranslatef(contents[i].joint[0], contents[i].joint[1], contents[i].joint[2]);
					gl.glRotatef(this.leftArmRotation, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i].joint[0], -contents[i].joint[1], -contents[i].joint[2]);
					gl.glTranslatef(contents[i - 1].joint[0], contents[i - 1].joint[1], contents[i - 1].joint[2]);
					gl.glRotatef(this.leftArmRotation - 6, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i - 1].joint[0], -contents[i - 1].joint[1], -contents[i - 1].joint[2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
				}
				else if(contents[i].part.equals("upperrightarm")) // right arm
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					gl.glTranslatef(contents[i].joint[0], contents[i].joint[1], contents[i].joint[2]);
					gl.glRotatef(this.rightArmRotation, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i].joint[0], -contents[i].joint[1], -contents[i].joint[2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
					
					this.rightArmRotation += rightArmModifier;
					
					if(this.rightArmRotation == -rightArmLimit)
					{
						rightArmModifier *= -1;
					}
					else if(this.rightArmRotation == rightArmLimit)
					{
						rightArmModifier *= -1;
					}
				}
				else if(contents[i].part.equals("lowerrightarm"))// end right arm
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					gl.glTranslatef(contents[i].joint[0], contents[i].joint[1], contents[i].joint[2]);
					gl.glRotatef(this.rightArmRotation, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i].joint[0], -contents[i].joint[1], -contents[i].joint[2]);
					gl.glTranslatef(contents[i - 1].joint[0], contents[i - 1].joint[1], contents[i - 1].joint[2]);
					gl.glRotatef(this.rightArmRotation - 6, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i - 1].joint[0], -contents[i - 1].joint[1], -contents[i - 1].joint[2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
				}
				else if(contents[i].part.equals("upperleftleg")) // left leg
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					gl.glTranslatef(contents[i].joint[0], contents[i].joint[1], contents[i].joint[2]);
					gl.glRotatef(this.leftLegRotation, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i].joint[0], -contents[i].joint[1], -contents[i].joint[2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
					
					this.leftLegRotation += leftLegModifier;
					
					if(this.leftLegRotation == -leftLegLimit)
					{
						leftLegModifier *= -1;
					}
					else if(this.leftLegRotation == leftLegLimit)
					{
						leftLegModifier *= -1;
					}
				}
				else if(contents[i].part.equals("lowerleftleg")) // end left leg
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					gl.glTranslatef(contents[i].joint[0], contents[i].joint[1], contents[i].joint[2]);
					gl.glRotatef(this.leftLegRotation, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i].joint[0], -contents[i].joint[1], -contents[i].joint[2]);
					gl.glTranslatef(contents[i - 1].joint[0], contents[i - 1].joint[1], contents[i - 1].joint[2]);
					gl.glRotatef(this.leftLegRotation + 5, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i - 1].joint[0], -contents[i - 1].joint[1], -contents[i - 1].joint[2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
				}
				else if(contents[i].part.equals("upperrightleg")) // right leg
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					gl.glTranslatef(contents[i].joint[0], contents[i].joint[1], contents[i].joint[2]);
					gl.glRotatef(this.rightLegRotation, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i].joint[0], -contents[i].joint[1], -contents[i].joint[2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
					
					this.rightLegRotation += rightLegModifier;
					
					if(this.rightLegRotation == -rightLegLimit)
					{
						rightLegModifier *= -1;
					}
					else if(this.rightLegRotation == rightLegLimit)
					{
						rightLegModifier *= -1;
					}
				}
				else // end right leg
				{
					gl.glPushMatrix();
					gl.glTranslatef(this.xPos, this.yPos, this.zPos);
					gl.glRotatef(this.yRotation, 0.0f, 1.0f, 0.0f);
					gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
					gl.glTranslatef(contents[i].joint[0], contents[i].joint[1], contents[i].joint[2]);
					gl.glRotatef(this.rightLegRotation, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i].joint[0], -contents[i].joint[1], -contents[i].joint[2]);
					gl.glTranslatef(contents[i - 1].joint[0], contents[i - 1].joint[1], contents[i - 1].joint[2]);
					gl.glRotatef(this.rightLegRotation + 5, 1.0f, 0.0f, 0.0f);
					gl.glTranslatef(-contents[i - 1].joint[0], -contents[i - 1].joint[1], -contents[i - 1].joint[2]);
					contents[i].draw(gl);
					gl.glPopMatrix();
				}
			}
		}
	}
}

class Scenery extends Shape {
	// this array can include other structures...
	private Shape[] contents;
	private float[][] positions;
	
	public float hiveRotation, hiveModifier;
	private final float hiveLimit = 10f;
	
	public float[][] boundingBox;
	public final float boxLength;
	
	public Scenery(Shape[] contents, float[][] positions) {
		super();
		init(contents, positions);
		this.boxLength = 1f;
		this.hiveRotation = hiveLimit;
		this.hiveModifier = -0.5f;
		updateBoundingBox();
	}
	
	public boolean hitTest(float[][] bBox)
	{
		boolean inside = false;
		
		for(int i = 0; i < this.boundingBox.length && !inside; i++)
		{
			for(int j = 0; j < bBox.length && !inside; j++)
			{
				inside = (bBox[j][0] >= this.boundingBox[0][0] && bBox[j][0] <= this.boundingBox[1][0] && bBox[j][1] >= this.boundingBox[2][1] && bBox[j][1] <= this.boundingBox[0][1]);
			}
		}
		
		return inside;
	}
	
	public void updateBoundingBox()
	{
		this.boundingBox = new float[][]{	{this.positions[0][0] - (this.boxLength / 2f), this.positions[0][2] + (this.boxLength / 2f)}, 
											{this.positions[0][0] + (this.boxLength / 2f), this.positions[0][2] + (this.boxLength / 2f)}, 
											{this.positions[0][0] + (this.boxLength / 2f), this.positions[0][2] - (this.boxLength / 2f)}, 
											{this.positions[0][0] - (this.boxLength / 2f), this.positions[0][2] - (this.boxLength / 2f)} };
	}
	
	public void drawBoundingBox(GL2 gl)
	{
		gl.glColor3f(0f, 0f, 0f);
		gl.glPointSize(5f);
		gl.glBegin(GL2.GL_POINTS);
		gl.glVertex3f(this.boundingBox[0][0], 0.0f, this.boundingBox[0][1]);
		gl.glVertex3f(this.boundingBox[1][0], 0.0f, this.boundingBox[1][1]);
		gl.glVertex3f(this.boundingBox[2][0], 0.0f, this.boundingBox[2][1]);
		gl.glVertex3f(this.boundingBox[3][0], 0.0f, this.boundingBox[3][1]);
		gl.glEnd();
	}
	
	private void init(Shape[] contents, float[][] positions) {
		this.contents = new Shape[contents.length];
		this.positions = new float[positions.length][3];
		System.arraycopy(contents, 0, this.contents, 0, contents.length);
		for (int i = 0; i < positions.length; i++) {
			System.arraycopy(positions[i], 0, this.positions[i], 0, 3);
		}
	}

	public void draw(GL2 gl) {
		super.draw(gl);
		for (int i = 0; i < contents.length; i++) 
		{	
			if(contents[i].part.equals("trunk"))
			{
				gl.glPushMatrix();
				gl.glTranslatef(positions[i][0], positions[i][1] * 2, positions[i][2]);
				gl.glScalef(2f, 2f, 2f);
				contents[i].draw(gl);
				gl.glPopMatrix();
			}
			else if(contents[i].part.equals("leavesbot"))
			{
				gl.glPushMatrix();
				gl.glTranslatef(positions[i][0], positions[i][1] * 2, positions[i][2]);
				gl.glScalef(2f, 2f, 2f);
				contents[i].draw(gl);
				gl.glPopMatrix();
			}
			else if(contents[i].part.equals("leavesmid"))
			{
				gl.glPushMatrix();
				gl.glTranslatef(positions[i][0], positions[i][1] * 2, positions[i][2]);
				gl.glScalef(2f, 2f, 2f);
				contents[i].draw(gl);
				gl.glPopMatrix();
			}
			else if(contents[i].part.equals("hive"))
			{
				gl.glPushMatrix();
				gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
				gl.glRotatef(this.hiveRotation, 1, 0, 0);
				gl.glRotatef(180, 1, 0, 0);
				gl.glScalef(0.7f, 0.7f, 0.7f);
				contents[i].draw(gl);
				gl.glPopMatrix();
				
				this.hiveRotation += hiveModifier;
				
				if(this.hiveRotation == -hiveLimit)
				{
					hiveModifier *= -1;
				}
				else if(this.hiveRotation == hiveLimit)
				{
					hiveModifier *= -1;
				}
			}
			else
			{
				gl.glPushMatrix();
				gl.glTranslatef(positions[i][0], positions[i][1], positions[i][2]);
				contents[i].draw(gl);
				gl.glPopMatrix();
			}
		}
	}
}

class Raindrop extends Shape {
	// this array can include other structures...
	private Shape[] contents;
	private float x, y, z;
	private float t, increment;
	
	public Raindrop() 
	{
		super();
		init(contents);
		this.x = (float)Math.random() * A4Q1.FLOOR_SIZE;
		this.y = (float)(Math.random() * (5f - 2f) + 2f);
		this.z = (float)Math.random() * A4Q1.FLOOR_SIZE;
		this.t = 0.0f;
		this.increment = 0.05f;
	}
	
	private void init(Shape[] contents) 
	{
		this.contents = new Shape[] {new Shape("resources/raindrop.obj", "raindrop")};
	}

	public void draw(GL2 gl) {
		super.draw(gl);
		for (int i = 0; i < contents.length; i++) 
		{	
			gl.glPushMatrix();
			gl.glTranslatef(this.x, lerp(this.t += this.increment, this.y, 0.0f), this.z);
			gl.glScalef(0.5f, 0.5f, 0.0f);
			contents[i].draw(gl);
			gl.glPopMatrix();
		}
	}
	
	private float lerp(float t, float a, float b) 
	{
		return (1 - t) * a + t * b;
	}
	
	public boolean isGrounded()
	{
		return this.t >= 1f;
	}
}