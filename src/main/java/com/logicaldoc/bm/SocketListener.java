package com.logicaldoc.bm;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketListener extends Thread {

	private MultiLoader loader;

	private int port;

	private boolean interrupted = false;

	private ServerSocket server = null;

	public SocketListener(MultiLoader loader, int port) {
		this.loader = loader;
		this.port = port;
	}

	public void close() {
		try {
			server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		interrupted = true;
	}

	@Override
	public void run() {

		// declaration section:
		// declare a server socket and a client socket for the server
		// declare an input and an output stream

		String command;
		//DataInputStream is;
		BufferedReader br;
		PrintStream os;
		Socket clientSocket = null;

		// Try to open a server socket on port 9999
		// Note that we can't choose a port less than 1023 if we are not
		// privileged users (root)
		try {
			server = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println(e);
		}

		// Create a socket object from the ServerSocket to listen and accept
		// connections.
		// Open input and output streams
		try {
			clientSocket = server.accept();
			//is = new DataInputStream(clientSocket.getInputStream());
			br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			os = new PrintStream(clientSocket.getOutputStream());

			// As long as we receive data, echo that data back to the client.
			while (!interrupted) {
				//command = is.readLine();
				command = br.readLine();

				if (command == null || command.isEmpty() || "exit".equals(command)) {
					if (clientSocket.isConnected())
						clientSocket.close();
					clientSocket = server.accept();
					//is = new DataInputStream(clientSocket.getInputStream());
					br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
					os = new PrintStream(clientSocket.getOutputStream());
				} else {
					System.out.println("Received command from socket: " + command);
					try {
						os.println(loader.processCommand(command));
						os.println("\n");
					} catch (InterruptedException e) {
					}
					
				}
			}
		} catch (IOException e) {
			System.out.println(e);
		}
	}
}