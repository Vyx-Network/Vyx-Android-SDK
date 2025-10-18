package vyxclient

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"strings"
	"sync"
	"time"

	"github.com/quic-go/quic-go"
)

// Callback is the interface that Android must implement to receive messages
// This is the Go Mobile compatible version (simpler name, simpler methods)
type Callback interface {
	// OnConnected is called when successfully connected and authenticated
	OnConnected()

	// OnDisconnected is called when connection is lost
	OnDisconnected(reason string)

	// OnMessage is called when a message is received from server
	// messageType: "connect", "data", "close", "ping", "auth_success", "error"
	// id: connection/message ID
	// addr: target address (for "connect" messages)
	// data: base64-encoded data (for "data" messages) or error message
	OnMessage(messageType string, id string, addr string, data string)

	// OnLog is called for logging (optional, can be empty implementation)
	OnLog(message string)
}

// Message represents the protocol message
type Message struct {
	Type string `json:"type"`
	ID   string `json:"id"`
	Addr string `json:"addr,omitempty"`
	Data string `json:"data,omitempty"`
}

// Connection represents a TCP connection to target
type Connection struct {
	conn     net.Conn
	dataChan chan []byte
}

// Client is the main QUIC client for Android (exported for Go Mobile)
type Client struct {
	serverURL   string
	apiToken    string
	clientType  string
	metadata    string
	callback    Callback
	quicConn    *quic.Conn
	quicStream  *quic.Stream
	quicMutex   sync.Mutex
	clientConns map[string]*Connection
	clientMutex sync.RWMutex
	ctx         context.Context
	cancel      context.CancelFunc
	isConnected bool
	shouldRun   bool
}

// NewClient creates a new QUIC client instance
// serverURL: server address (e.g., "api.vyx.network:8443")
// apiToken: authentication token from dashboard
// clientType: "android_sdk" or similar
// metadata: JSON string with device info
// callback: Callback implementation for receiving events
func NewClient(serverURL string, apiToken string, clientType string, metadata string, callback Callback) *Client {
	ctx, cancel := context.WithCancel(context.Background())

	return &Client{
		serverURL:   serverURL,
		apiToken:    apiToken,
		clientType:  clientType,
		metadata:    metadata,
		callback:    callback,
		clientConns: make(map[string]*Connection),
		ctx:         ctx,
		cancel:      cancel,
		shouldRun:   true,
	}
}

// Start begins the connection loop with automatic reconnection
func (c *Client) Start() {
	go c.connectionLoop()
}

// Stop disconnects and stops reconnection attempts
func (c *Client) Stop() {
	c.shouldRun = false
	c.cancel()
	c.disconnect()
}

// SendMessage sends a message to the server
// Returns error message or empty string on success
func (c *Client) SendMessage(messageType string, id string, addr string, data string) string {
	msg := &Message{
		Type: messageType,
		ID:   id,
		Addr: addr,
		Data: data,
	}

	if err := c.sendMessage(msg); err != nil {
		return err.Error()
	}
	return ""
}

// IsConnected returns true if currently connected
func (c *Client) IsConnected() bool {
	c.quicMutex.Lock()
	defer c.quicMutex.Unlock()
	return c.isConnected
}

// connectionLoop handles automatic reconnection
func (c *Client) connectionLoop() {
	connectionAttempts := 0
	retryDelay := 4 * time.Second

	for c.shouldRun {
		c.log(fmt.Sprintf("Attempting to connect (attempt %d)", connectionAttempts+1))

		if c.connect() {
			connectionAttempts = 0
			if c.callback != nil {
				c.callback.OnConnected()
			}
			c.log("Successfully connected and authenticated")

			// Wait for disconnection
			c.waitForDisconnection()

			if c.callback != nil {
				c.callback.OnDisconnected("Connection lost")
			}
			c.log("Connection lost, will reconnect...")
		}

		// Determine retry delay
		if connectionAttempts >= 2 {
			retryDelay = 5 * time.Minute
		}

		connectionAttempts++

		if c.shouldRun {
			c.log(fmt.Sprintf("Retrying in %v...", retryDelay))
			time.Sleep(retryDelay)
		}
	}
}

// connect establishes QUIC connection and authenticates
func (c *Client) connect() bool {
	serverAddr := c.serverURL
	if !strings.Contains(serverAddr, ":") {
		serverAddr = serverAddr + ":8443"
	}

	// Build TLS config
	tlsConf := c.buildTLSConfig(serverAddr)

	// Dial QUIC
	conn, err := quic.DialAddr(c.ctx, serverAddr, tlsConf, nil)
	if err != nil {
		c.log(fmt.Sprintf("Failed to connect: %v", err))
		return false
	}

	// Wait briefly for server to accept
	time.Sleep(100 * time.Millisecond)

	// Open stream
	stream, err := conn.OpenStreamSync(c.ctx)
	if err != nil {
		c.log(fmt.Sprintf("Failed to open stream: %v", err))
		conn.CloseWithError(1, "failed to open stream")
		return false
	}

	c.quicMutex.Lock()
	c.quicConn = conn
	c.quicStream = stream
	c.isConnected = true
	c.quicMutex.Unlock()

	// Authenticate
	if !c.authenticate(stream) {
		c.log("Authentication failed")
		conn.CloseWithError(1, "authentication failed")
		c.quicMutex.Lock()
		c.isConnected = false
		c.quicMutex.Unlock()
		return false
	}

	c.log("Authenticated successfully")

	// Start reading messages
	c.readMessages(stream)

	return true
}

// buildTLSConfig creates TLS configuration
func (c *Client) buildTLSConfig(serverAddr string) *tls.Config {
	config := &tls.Config{
		NextProtos: []string{"vyx-proxy"},
		MinVersion: tls.VersionTLS12,
	}

	// Extract hostname
	host := serverAddr
	if strings.Contains(serverAddr, ":") {
		host, _, _ = net.SplitHostPort(serverAddr)
	}

	// Development mode for localhost
	if host == "localhost" || host == "127.0.0.1" {
		c.log("Development mode: Using InsecureSkipVerify")
		config.InsecureSkipVerify = true
	} else {
		// Production mode
		c.log(fmt.Sprintf("Production mode: Verifying TLS for %s", host))
		config.ServerName = host
		config.InsecureSkipVerify = false
	}

	return config
}

// authenticate sends authentication to server
func (c *Client) authenticate(stream *quic.Stream) bool {
	authMsg := Message{
		Type: "auth",
		ID:   c.apiToken,
		Data: c.metadata,
	}

	c.log("Sending authentication...")
	encoder := json.NewEncoder(stream)
	if err := encoder.Encode(authMsg); err != nil {
		c.log(fmt.Sprintf("Failed to send auth: %v", err))
		return false
	}

	// Wait for response with timeout
	responseChan := make(chan Message, 1)
	errorChan := make(chan error, 1)

	go func() {
		decoder := json.NewDecoder(stream)
		var response Message
		if err := decoder.Decode(&response); err != nil {
			errorChan <- err
			return
		}
		responseChan <- response
	}()

	select {
	case response := <-responseChan:
		c.log(fmt.Sprintf("Auth response: %s", response.Type))
		if response.Type == "auth_success" {
			// Notify Android
			if c.callback != nil {
				c.callback.OnMessage("auth_success", response.ID, "", response.Data)
			}
			return true
		}
		if response.Type == "error" {
			if c.callback != nil {
				c.callback.OnMessage("error", response.ID, "", response.Data)
			}
			return false
		}
		return false
	case err := <-errorChan:
		c.log(fmt.Sprintf("Auth response error: %v", err))
		return false
	case <-time.After(10 * time.Second):
		c.log("Authentication timeout")
		return false
	}
}

// readMessages reads messages from QUIC stream
func (c *Client) readMessages(stream *quic.Stream) {
	decoder := json.NewDecoder(stream)

	for c.shouldRun {
		var msg Message
		err := decoder.Decode(&msg)
		if err != nil {
			c.log(fmt.Sprintf("Read error: %v", err))

			// Close all client connections
			c.clientMutex.Lock()
			for id, cc := range c.clientConns {
				cc.conn.Close()
				close(cc.dataChan)
				delete(c.clientConns, id)
			}
			c.clientMutex.Unlock()

			c.quicMutex.Lock()
			c.isConnected = false
			c.quicMutex.Unlock()

			return
		}

		c.log(fmt.Sprintf("Received: %s", msg.Type))
		c.handleMessage(&msg)
	}
}

// handleMessage processes incoming messages
func (c *Client) handleMessage(msg *Message) {
	if c.callback == nil {
		return
	}

	switch msg.Type {
	case "connect":
		// Forward to Android to handle the TCP connection
		c.callback.OnMessage("connect", msg.ID, msg.Addr, msg.Data)

	case "data":
		// Forward data to existing connection
		c.callback.OnMessage("data", msg.ID, "", msg.Data)

	case "close":
		// Close connection
		c.clientMutex.Lock()
		if cc, ok := c.clientConns[msg.ID]; ok {
			cc.conn.Close()
			close(cc.dataChan)
			delete(c.clientConns, msg.ID)
		}
		c.clientMutex.Unlock()
		c.callback.OnMessage("close", msg.ID, "", "")

	case "ping":
		// Respond with pong
		c.sendMessage(&Message{
			Type: "pong",
			ID:   msg.ID,
		})

	case "error":
		c.callback.OnMessage("error", msg.ID, "", msg.Data)

	default:
		c.log(fmt.Sprintf("Unknown message type: %s", msg.Type))
	}
}

// sendMessage sends a message to server
func (c *Client) sendMessage(msg *Message) error {
	c.quicMutex.Lock()
	defer c.quicMutex.Unlock()

	if c.quicStream == nil {
		return fmt.Errorf("no active QUIC stream")
	}

	data, err := json.Marshal(msg)
	if err != nil {
		return fmt.Errorf("failed to marshal message: %w", err)
	}
	data = append(data, '\n')

	_, err = c.quicStream.Write(data)
	if err != nil {
		return fmt.Errorf("failed to write to stream: %w", err)
	}

	return nil
}

// RegisterConnection registers a TCP connection (called from Android after successful TCP connect)
// Note: This method is not exported for Go Mobile (uses net.Conn which can't be bound)
func (c *Client) registerConnection(id string, conn net.Conn) {
	dataChan := make(chan []byte, 10000)
	cc := &Connection{conn: conn, dataChan: dataChan}

	c.clientMutex.Lock()
	c.clientConns[id] = cc
	c.clientMutex.Unlock()

	// Start relay goroutines
	go c.relayFromConnToQuic(cc, id)
	go c.relayFromChanToConn(cc, id)
}

// relayFromConnToQuic reads from TCP connection and sends to QUIC
func (c *Client) relayFromConnToQuic(cc *Connection, id string) {
	buffer := make([]byte, 32768)
	for {
		n, err := cc.conn.Read(buffer)
		if err != nil {
			c.sendMessage(&Message{Type: "close", ID: id})
			c.clientMutex.Lock()
			if _, ok := c.clientConns[id]; ok {
				cc.conn.Close()
				close(cc.dataChan)
				delete(c.clientConns, id)
			}
			c.clientMutex.Unlock()
			return
		}

		if n > 0 {
			encoded := base64.StdEncoding.EncodeToString(buffer[:n])
			c.sendMessage(&Message{
				Type: "data",
				ID:   id,
				Data: encoded,
			})
		}
	}
}

// relayFromChanToConn reads from channel and writes to TCP connection
func (c *Client) relayFromChanToConn(cc *Connection, id string) {
	for data := range cc.dataChan {
		_, err := cc.conn.Write(data)
		if err != nil {
			c.sendMessage(&Message{Type: "close", ID: id})
			c.clientMutex.Lock()
			if _, ok := c.clientConns[id]; ok {
				cc.conn.Close()
				close(cc.dataChan)
				delete(c.clientConns, id)
			}
			c.clientMutex.Unlock()
			return
		}
	}
}

// disconnect closes the QUIC connection
func (c *Client) disconnect() {
	c.quicMutex.Lock()
	defer c.quicMutex.Unlock()

	if c.quicConn != nil {
		c.quicConn.CloseWithError(0, "client stopped")
		c.quicConn = nil
	}

	if c.quicStream != nil {
		c.quicStream.Close()
		c.quicStream = nil
	}

	c.isConnected = false

	// Close all client connections
	c.clientMutex.Lock()
	for id, cc := range c.clientConns {
		cc.conn.Close()
		close(cc.dataChan)
		delete(c.clientConns, id)
	}
	c.clientMutex.Unlock()
}

// waitForDisconnection blocks until disconnected
func (c *Client) waitForDisconnection() {
	for c.isConnected && c.shouldRun {
		time.Sleep(1 * time.Second)
	}
}

// log sends log message to Android
func (c *Client) log(message string) {
	log.Println(message)
	if c.callback != nil {
		c.callback.OnLog(message)
	}
}
