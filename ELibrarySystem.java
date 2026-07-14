/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package elibrarysystem;

/**
 *
 * @author admin
 */
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.*;

/*
 * ELibrarySystem.java
 * Single-file Swing + JDBC example implementing an E-Library Management System.
 * Features:
 *  - Admin login
 *  - Manage books and users
 *  - Issue and return books
 *  - Generate late fee reports (CSV)
 *  - Search and sort books/users
 *  - Uses JDBC (SQLite), Collections, File I/O
 *
 * Note: Requires sqlite-jdbc driver on classpath when compiling/running.
 * Example compile/run:
 * javac -cp .:sqlite-jdbc-3.41.2.1.jar ELibrarySystem.java
 * java -cp .:sqlite-jdbc-3.41.2.1.jar ELibrarySystem
 */

public class ELibrarySystem {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Database.init();
                new LoginFrame();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Fatal error: " + e.getMessage());
            }
        });
    }
}

/* ---------------------- Database helper ---------------------- */
class Database {
    private static final String DB_FILE = "elibrary.db";
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    static void init() throws SQLException, IOException {
        boolean exists = Files.exists(Path.of(DB_FILE));
        try (Connection conn = getConnection()) {
            if (!exists) {
                createTables(conn);
                seedData(conn);
            }
        }
    }

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private static void createTables(Connection conn) throws SQLException {
        String createAdmin = "CREATE TABLE IF NOT EXISTS admin (id INTEGER PRIMARY KEY, username TEXT UNIQUE, password TEXT)";
        String createBooks = "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, title TEXT, author TEXT, isbn TEXT UNIQUE, total INTEGER, available INTEGER)";
        String createUsers = "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT, email TEXT UNIQUE, phone TEXT)";
        String createTransactions = "CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY, user_id INTEGER, book_id INTEGER, issue_date TEXT, due_date TEXT, return_date TEXT, fee REAL DEFAULT 0, FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(book_id) REFERENCES books(id))";
        try (Statement st = conn.createStatement()) {
            st.execute(createAdmin);
            st.execute(createBooks);
            st.execute(createUsers);
            st.execute(createTransactions);
        }
    }

    private static void seedData(Connection conn) throws SQLException {
        // default admin: admin / admin123
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO admin(username,password) VALUES(?,?)")) {
            ps.setString(1, "admin");
            ps.setString(2, "admin123");
            ps.executeUpdate();
        }
        // sample books
        String[][] books = {
                {"The Hobbit", "J.R.R. Tolkien", "9780261102217", "5"},
                {"Effective Java", "Joshua Bloch", "9780134685991", "3"},
                {"Clean Code", "Robert C. Martin", "9780132350884", "4"},
                {"Design Patterns", "Erich Gamma", "9780201633610", "2"},
                {"Algorithms", "Robert Sedgewick", "9780321573513", "6"}
        };
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO books(title,author,isbn,total,available) VALUES(?,?,?,?,?)")) {
            for (String[] b : books) {
                ps.setString(1, b[0]);
                ps.setString(2, b[1]);
                ps.setString(3, b[2]);
                ps.setInt(4, Integer.parseInt(b[3]));
                ps.setInt(5, Integer.parseInt(b[3]));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        // sample users
        String[][] users = {
                {"Alice Johnson", "alice@example.com", "9876543210"},
                {"Bob Kumar", "bob@example.com", "8765432109"}
        };
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO users(name,email,phone) VALUES(?,?,?)")) {
            for (String[] u : users) {
                ps.setString(1, u[0]);
                ps.setString(2, u[1]);
                ps.setString(3, u[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}

/* ---------------------- Models ---------------------- */
class Book {
    int id;
    String title;
    String author;
    String isbn;
    int total;
    int available;

    public Book(int id, String title, String author, String isbn, int total, int available) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.total = total;
        this.available = available;
    }
}

class UserModel {
    int id;
    String name;
    String email;
    String phone;

    public UserModel(int id, String name, String email, String phone) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }
}

class TransactionModel {
    int id;
    int userId;
    int bookId;
    LocalDate issueDate;
    LocalDate dueDate;
    LocalDate returnDate; // null if not returned
    double fee;
}

/* ---------------------- DAO utilities ---------------------- */
class LibraryDAO {
    // Books CRUD
    static List<Book> getAllBooks() throws SQLException {
        List<Book> list = new ArrayList<>();
        String q = "SELECT * FROM books";
        try (Connection c = Database.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) {
            while (rs.next()) {
                list.add(new Book(rs.getInt("id"), rs.getString("title"), rs.getString("author"), rs.getString("isbn"), rs.getInt("total"), rs.getInt("available")));
            }
        }
        return list;
    }

    static void addBook(String title, String author, String isbn, int copies) throws SQLException {
        String q = "INSERT INTO books(title,author,isbn,total,available) VALUES(?,?,?,?,?)";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, isbn);
            ps.setInt(4, copies);
            ps.setInt(5, copies);
            ps.executeUpdate();
        }
    }

    static void updateBook(Book b) throws SQLException {
        String q = "UPDATE books SET title=?,author=?,isbn=?,total=?,available=? WHERE id=?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, b.title);
            ps.setString(2, b.author);
            ps.setString(3, b.isbn);
            ps.setInt(4, b.total);
            ps.setInt(5, b.available);
            ps.setInt(6, b.id);
            ps.executeUpdate();
        }
    }

    static void deleteBook(int id) throws SQLException {
        String q = "DELETE FROM books WHERE id=?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // Users CRUD
    static List<UserModel> getAllUsers() throws SQLException {
        List<UserModel> list = new ArrayList<>();
        String q = "SELECT * FROM users";
        try (Connection c = Database.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) {
            while (rs.next()) {
                list.add(new UserModel(rs.getInt("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone")));
            }
        }
        return list;
    }

    static int addUser(String name, String email, String phone) throws SQLException {
        String q = "INSERT INTO users(name,email,phone) VALUES(?,?,?)";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(q, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    static void updateUser(UserModel u) throws SQLException {
        String q = "UPDATE users SET name=?,email=?,phone=? WHERE id=?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, u.name);
            ps.setString(2, u.email);
            ps.setString(3, u.phone);
            ps.setInt(4, u.id);
            ps.executeUpdate();
        }
    }

    static void deleteUser(int id) throws SQLException {
        String q = "DELETE FROM users WHERE id=?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // Transactions: issue/return
    static void issueBook(int userId, int bookId, LocalDate issueDate, LocalDate dueDate) throws SQLException {
        String insert = "INSERT INTO transactions(user_id,book_id,issue_date,due_date) VALUES(?,?,?,?)";
        String updateBook = "UPDATE books SET available = available - 1 WHERE id = ? AND available > 0";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(insert); PreparedStatement ps2 = c.prepareStatement(updateBook)) {
            c.setAutoCommit(false);
            ps.setInt(1, userId);
            ps.setInt(2, bookId);
            ps.setString(3, issueDate.toString());
            ps.setString(4, dueDate.toString());
            ps.executeUpdate();

            ps2.setInt(1, bookId);
            int affected = ps2.executeUpdate();
            if (affected == 0) {
                c.rollback();
                throw new SQLException("Book not available for issuance (none available)");
            }
            c.commit();
        }
    }

    static void returnBook(int transactionId, LocalDate returnDate, double fee) throws SQLException {
        String updTrans = "UPDATE transactions SET return_date=?, fee=? WHERE id=?";
        String updBook = "UPDATE books SET available = available + 1 WHERE id = (SELECT book_id FROM transactions WHERE id=?)";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(updTrans); PreparedStatement ps2 = c.prepareStatement(updBook)) {
            c.setAutoCommit(false);
            ps.setString(1, returnDate.toString());
            ps.setDouble(2, fee);
            ps.setInt(3, transactionId);
            ps.executeUpdate();

            ps2.setInt(1, transactionId);
            ps2.executeUpdate();
            c.commit();
        }
    }

    static List<Map<String, Object>> getActiveTransactions() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String q = "SELECT t.id,t.user_id,t.book_id,t.issue_date,t.due_date,t.return_date,t.fee,u.name as user_name,b.title as book_title FROM transactions t JOIN users u ON t.user_id=u.id JOIN books b ON t.book_id=b.id";
        try (Connection c = Database.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("user_id", rs.getInt("user_id"));
                m.put("book_id", rs.getInt("book_id"));
                m.put("issue_date", rs.getString("issue_date"));
                m.put("due_date", rs.getString("due_date"));
                m.put("return_date", rs.getString("return_date"));
                m.put("fee", rs.getDouble("fee"));
                m.put("user_name", rs.getString("user_name"));
                m.put("book_title", rs.getString("book_title"));
                list.add(m);
            }
        }
        return list;
    }

    static boolean validateAdmin(String username, String password) throws SQLException {
        String q = "SELECT COUNT(*) FROM admin WHERE username=? AND password=?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getInt(1) > 0;
            }
        }
    }
}

/* ---------------------- Login Frame ---------------------- */
class LoginFrame extends JFrame {
    JTextField userField;
    JPasswordField passField;

    LoginFrame() {
        setTitle("E-Library Admin Login");
        setSize(360, 200);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.gridx = 0; c.gridy = 0; p.add(new JLabel("Username:"), c);
        c.gridx = 1; userField = new JTextField(15); p.add(userField, c);
        c.gridx = 0; c.gridy = 1; p.add(new JLabel("Password:"), c);
        c.gridx = 1; passField = new JPasswordField(15); p.add(passField, c);

        JPanel buttons = new JPanel();
        JButton login = new JButton("Login");
        JButton quit = new JButton("Quit");
        buttons.add(login); buttons.add(quit);

        add(p, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        login.addActionListener(e -> doLogin());
        quit.addActionListener(e -> System.exit(0));

        getRootPane().setDefaultButton(login);
        setVisible(true);
    }

    private void doLogin() {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword());
        try {
            if (LibraryDAO.validateAdmin(u, p)) {
                dispose();
                new MainFrame(u);
            } else {
                JOptionPane.showMessageDialog(this, "Invalid credentials", "Login failed", JOptionPane.ERROR_MESSAGE);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }
}

/* ---------------------- Main Application Frame ---------------------- */
class MainFrame extends JFrame {
    String adminUser;
    JTabbedPane tabs;

    BooksPanel booksPanel;
    UsersPanel usersPanel;
    TransactionsPanel transactionsPanel;

    MainFrame(String adminUser) {
        this.adminUser = adminUser;
        setTitle("E-Library Management System - Admin: " + adminUser);
        setSize(1000, 640);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        tabs = new JTabbedPane();
        booksPanel = new BooksPanel();
        usersPanel = new UsersPanel();
        transactionsPanel = new TransactionsPanel();

        tabs.addTab("Books", booksPanel);
        tabs.addTab("Users", usersPanel);
        tabs.addTab("Transactions", transactionsPanel);

        add(tabs, BorderLayout.CENTER);

        // toolbar
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        JButton backupBtn = new JButton("Backup DB");
        backupBtn.addActionListener(e -> backupDatabase());
        tb.add(backupBtn);
        JButton reportBtn = new JButton("Late Fee Report (CSV)");
        reportBtn.addActionListener(e -> transactionsPanel.generateLateFeeReport());
        tb.add(reportBtn);
        add(tb, BorderLayout.NORTH);

        setVisible(true);
    }

    private void backupDatabase() {
        try {
            Path src = Path.of("elibrary.db");
            if (!Files.exists(src)) {
                JOptionPane.showMessageDialog(this, "Database file not found to backup");
                return;
            }
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            Path dst = Path.of("backup_elibrary_" + stamp + ".db");
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(this, "Backup created: " + dst.toString());
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Backup failed: " + ex.getMessage());
        }
    }
}

/* ---------------------- Books Panel ---------------------- */
class BooksPanel extends JPanel {
    DefaultTableModel model;
    JTable table;
    JTextField searchField;
    JComboBox<String> sortBox;

    List<Book> booksCache = new ArrayList<>();

    BooksPanel() {
        setLayout(new BorderLayout());
        model = new DefaultTableModel(new String[]{"ID","Title","Author","ISBN","Total","Available"}, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(20);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> reloadTable());
        top.add(new JLabel("Search:")); top.add(searchField); top.add(searchBtn);

        sortBox = new JComboBox<>(new String[]{"Sort: ID","Sort: Title","Sort: Author","Sort: Available Desc"});
        sortBox.addActionListener(e -> reloadTable());
        top.add(sortBox);

        add(top, BorderLayout.NORTH);

        JPanel south = new JPanel();
        JButton add = new JButton("Add Book");
        JButton edit = new JButton("Edit Book");
        JButton del = new JButton("Delete Book");
        JButton refresh = new JButton("Refresh");
        south.add(add); south.add(edit); south.add(del); south.add(refresh);
        add(south, BorderLayout.SOUTH);

        add.addActionListener(e -> addBookDialog());
        edit.addActionListener(e -> editSelectedBook());
        del.addActionListener(e -> deleteSelectedBook());
        refresh.addActionListener(e -> reloadTable());

        reloadTable();
    }

    void reloadTable() {
        model.setRowCount(0);
        try {
            booksCache = LibraryDAO.getAllBooks();
            // search filter
            String q = searchField.getText().trim().toLowerCase();
            Stream<Book> s = booksCache.stream();
            if (!q.isEmpty()) {
                s = s.filter(b -> b.title.toLowerCase().contains(q) || b.author.toLowerCase().contains(q) || b.isbn.toLowerCase().contains(q));
            }
            List<Book> list = s.toList();
            // sorting
            String selected = (String)sortBox.getSelectedItem();
            if (selected != null) {
                switch (selected) {
                    case "Sort: Title": list.sort(Comparator.comparing(b -> b.title.toLowerCase())); break;
                    case "Sort: Author": list.sort(Comparator.comparing(b -> b.author.toLowerCase())); break;
                    case "Sort: Available Desc": list.sort(Comparator.comparingInt((Book b) -> b.available).reversed()); break;
                    default: list.sort(Comparator.comparingInt(b -> b.id));
                }
            }
            for (Book b : list) model.addRow(new Object[]{b.id,b.title,b.author,b.isbn,b.total,b.available});
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading books: " + ex.getMessage());
        }
    }

    private void addBookDialog() {
        JTextField tTitle = new JTextField(20);
        JTextField tAuthor = new JTextField(20);
        JTextField tIsbn = new JTextField(20);
        JTextField tCopies = new JTextField(5);
        JPanel p = new JPanel(new GridLayout(0,2));
        p.add(new JLabel("Title:")); p.add(tTitle);
        p.add(new JLabel("Author:")); p.add(tAuthor);
        p.add(new JLabel("ISBN:")); p.add(tIsbn);
        p.add(new JLabel("Copies:")); p.add(tCopies);
        int r = JOptionPane.showConfirmDialog(this, p, "Add Book", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            try {
                String title = tTitle.getText().trim();
                String author = tAuthor.getText().trim();
                String isbn = tIsbn.getText().trim();
                int copies = Integer.parseInt(tCopies.getText().trim());
                LibraryDAO.addBook(title, author, isbn, copies);
                reloadTable();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to add book: " + ex.getMessage());
            }
        }
    }

    private void editSelectedBook() {
        int r = table.getSelectedRow();
        if (r == -1) { JOptionPane.showMessageDialog(this, "Select a book first"); return; }
        int id = (int) model.getValueAt(r,0);
        try {
            Book b = booksCache.stream().filter(x -> x.id == id).findFirst().orElse(null);
            if (b==null) return;
            JTextField tTitle = new JTextField(b.title,20);
            JTextField tAuthor = new JTextField(b.author,20);
            JTextField tIsbn = new JTextField(b.isbn,20);
            JTextField tTotal = new JTextField(String.valueOf(b.total),5);
            JTextField tAvail = new JTextField(String.valueOf(b.available),5);
            JPanel p = new JPanel(new GridLayout(0,2));
            p.add(new JLabel("Title:")); p.add(tTitle);
            p.add(new JLabel("Author:")); p.add(tAuthor);
            p.add(new JLabel("ISBN:")); p.add(tIsbn);
            p.add(new JLabel("Total Copies:")); p.add(tTotal);
            p.add(new JLabel("Available:")); p.add(tAvail);
            int res = JOptionPane.showConfirmDialog(this, p, "Edit Book", JOptionPane.OK_CANCEL_OPTION);
            if (res == JOptionPane.OK_OPTION) {
                b.title = tTitle.getText().trim();
                b.author = tAuthor.getText().trim();
                b.isbn = tIsbn.getText().trim();
                b.total = Integer.parseInt(tTotal.getText().trim());
                b.available = Integer.parseInt(tAvail.getText().trim());
                LibraryDAO.updateBook(b);
                reloadTable();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Edit failed: " + ex.getMessage());
        }
    }

    private void deleteSelectedBook() {
        int r = table.getSelectedRow();
        if (r == -1) { JOptionPane.showMessageDialog(this, "Select a book first"); return; }
        int id = (int) model.getValueAt(r,0);
        int c = JOptionPane.showConfirmDialog(this, "Delete book ID " + id + "?","Confirm",JOptionPane.YES_NO_OPTION);
        if (c==JOptionPane.YES_OPTION) {
            try { LibraryDAO.deleteBook(id); reloadTable(); } catch (SQLException ex) { JOptionPane.showMessageDialog(this, "Delete failed: "+ex.getMessage()); }
        }
    }
}

/* ---------------------- Users Panel ---------------------- */
class UsersPanel extends JPanel {
    DefaultTableModel model;
    JTable table;
    JTextField searchField;
    List<UserModel> usersCache = new ArrayList<>();

    UsersPanel() {
        setLayout(new BorderLayout());
        model = new DefaultTableModel(new String[]{"ID","Name","Email","Phone"}, 0) {
            public boolean isCellEditable(int row,int col) { return false; }
        };
        table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(20);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> reloadTable());
        top.add(new JLabel("Search:")); top.add(searchField); top.add(searchBtn);
        add(top, BorderLayout.NORTH);
        JPanel south = new JPanel();
        JButton add = new JButton("Add User");
        JButton edit = new JButton("Edit User");
        JButton del = new JButton("Delete User");
        south.add(add); south.add(edit); south.add(del);
        add(south, BorderLayout.SOUTH);
        add.addActionListener(e -> addUserDialog());
        edit.addActionListener(e -> editSelectedUser());
        del.addActionListener(e -> deleteSelectedUser());
        reloadTable();
    }

    void reloadTable() {
        model.setRowCount(0);
        try {
            usersCache = LibraryDAO.getAllUsers();
            String q = searchField.getText().trim().toLowerCase();
            usersCache.stream()
                    .filter(u -> q.isEmpty() || u.name.toLowerCase().contains(q) || u.email.toLowerCase().contains(q) || u.phone.contains(q))
                    .forEach(u -> model.addRow(new Object[]{u.id,u.name,u.email,u.phone}));
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading users: " + ex.getMessage());
        }
    }

    private void addUserDialog() {
        JTextField tName = new JTextField(20);
        JTextField tEmail = new JTextField(20);
        JTextField tPhone = new JTextField(10);
        JPanel p = new JPanel(new GridLayout(0,2));
        p.add(new JLabel("Name:")); p.add(tName);
        p.add(new JLabel("Email:")); p.add(tEmail);
        p.add(new JLabel("Phone:")); p.add(tPhone);
        int r = JOptionPane.showConfirmDialog(this, p, "Add User", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            try {
                LibraryDAO.addUser(tName.getText().trim(), tEmail.getText().trim(), tPhone.getText().trim());
                reloadTable();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to add user: " + ex.getMessage());
            }
        }
    }

    private void editSelectedUser() {
        int r = table.getSelectedRow();
        if (r == -1) { JOptionPane.showMessageDialog(this, "Select a user first"); return; }
        int id = (int) model.getValueAt(r,0);
        try {
            UserModel u = usersCache.stream().filter(x -> x.id == id).findFirst().orElse(null);
            if (u==null) return;
            JTextField tName = new JTextField(u.name,20);
            JTextField tEmail = new JTextField(u.email,20);
            JTextField tPhone = new JTextField(u.phone,10);
            JPanel p = new JPanel(new GridLayout(0,2));
            p.add(new JLabel("Name:")); p.add(tName);
            p.add(new JLabel("Email:")); p.add(tEmail);
            p.add(new JLabel("Phone:")); p.add(tPhone);
            int res = JOptionPane.showConfirmDialog(this, p, "Edit User", JOptionPane.OK_CANCEL_OPTION);
            if (res==JOptionPane.OK_OPTION) {
                u.name = tName.getText().trim(); u.email = tEmail.getText().trim(); u.phone = tPhone.getText().trim();
                LibraryDAO.updateUser(u); reloadTable();
            }
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Edit failed: " + ex.getMessage()); }
    }

    private void deleteSelectedUser() {
        int r = table.getSelectedRow();
        if (r == -1) { JOptionPane.showMessageDialog(this, "Select a user first"); return; }
        int id = (int) model.getValueAt(r,0);
        int c = JOptionPane.showConfirmDialog(this, "Delete user ID " + id + "?","Confirm",JOptionPane.YES_NO_OPTION);
        if (c==JOptionPane.YES_OPTION) {
            try { LibraryDAO.deleteUser(id); reloadTable(); } catch (SQLException ex) { JOptionPane.showMessageDialog(this, "Delete failed: "+ex.getMessage()); }
        }
    }
}

/* ---------------------- Transactions Panel ---------------------- */
class TransactionsPanel extends JPanel {
    DefaultTableModel model;
    JTable table;

    TransactionsPanel() {
        setLayout(new BorderLayout());
        model = new DefaultTableModel(new String[]{"Txn ID","User","Book","Issue Date","Due Date","Return Date","Fee"}, 0) {
            public boolean isCellEditable(int r,int c) { return false; }
        };
        table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel();
        JButton issue = new JButton("Issue Book");
        JButton ret = new JButton("Return Book");
        JButton refresh = new JButton("Refresh");
        south.add(issue); south.add(ret); south.add(refresh);
        add(south, BorderLayout.SOUTH);

        issue.addActionListener(e -> issueDialog());
        ret.addActionListener(e -> returnDialog());
        refresh.addActionListener(e -> reloadTable());

        reloadTable();
    }

    void reloadTable() {
        model.setRowCount(0);
        try {
            List<Map<String,Object>> txns = LibraryDAO.getActiveTransactions();
            for (Map<String,Object> m : txns) {
                model.addRow(new Object[]{m.get("id"), m.get("user_name"), m.get("book_title"), m.get("issue_date"), m.get("due_date"), m.get("return_date"), m.get("fee")});
            }
        } catch (SQLException ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Error loading transactions: " + ex.getMessage()); }
    }

    private void issueDialog() {
        try {
            List<UserModel> users = LibraryDAO.getAllUsers();
            List<Book> books = LibraryDAO.getAllBooks();
            if (users.isEmpty() || books.isEmpty()) { JOptionPane.showMessageDialog(this, "Need at least one user and one book to issue"); return; }
            JComboBox<String> userBox = new JComboBox<>();
            users.forEach(u -> userBox.addItem(u.id + ": " + u.name + " (" + u.email + ")"));
            JComboBox<String> bookBox = new JComboBox<>();
            books.forEach(b -> bookBox.addItem(b.id + ": " + b.title + " [avail:" + b.available + "]"));
            JTextField dueDays = new JTextField("14",4);
            JPanel p = new JPanel(new GridLayout(0,1));
            p.add(new JLabel("Select User:")); p.add(userBox);
            p.add(new JLabel("Select Book:")); p.add(bookBox);
            p.add(new JLabel("Loan Period (days):")); p.add(dueDays);
            int r = JOptionPane.showConfirmDialog(this, p, "Issue Book", JOptionPane.OK_CANCEL_OPTION);
            if (r==JOptionPane.OK_OPTION) {
                int uid = Integer.parseInt(((String)userBox.getSelectedItem()).split(":" )[0]);
                int bid = Integer.parseInt(((String)bookBox.getSelectedItem()).split(":" )[0]);
                int days = Integer.parseInt(dueDays.getText().trim());
                LocalDate issue = LocalDate.now();
                LocalDate due = issue.plusDays(days);
                LibraryDAO.issueBook(uid, bid, issue, due);
                JOptionPane.showMessageDialog(this, "Book issued until " + due.toString());
                reloadTable();
            }
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Issue failed: " + ex.getMessage()); }
    }

    private void returnDialog() {
        int r = table.getSelectedRow();
        if (r == -1) { JOptionPane.showMessageDialog(this, "Select a transaction first"); return; }
        int txnId = (int) model.getValueAt(r,0);
        String dueDateStr = (String) model.getValueAt(r,4);
        try {
            LocalDate due = LocalDate.parse(dueDateStr);
            LocalDate ret = LocalDate.now();
            long daysLate = ChronoUnit.DAYS.between(due, ret);
            double fee = 0;
            if (daysLate > 0) fee = daysLate * 5.0; // ₹5 per day late fee
            int c = JOptionPane.showConfirmDialog(this, "Return book now?\nDue: " + due + "\nReturn: " + ret + "\nLate days: " + (daysLate>0?daysLate:0) + "\nFee: " + fee, "Confirm Return", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                LibraryDAO.returnBook(txnId, ret, fee);
                JOptionPane.showMessageDialog(this, "Book returned. Fee: " + fee);
                reloadTable();
            }
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Return failed: " + ex.getMessage()); }
    }

    void generateLateFeeReport() {
        try {
            List<Map<String,Object>> txns = LibraryDAO.getActiveTransactions();
            List<String> lines = new ArrayList<>();
            lines.add("TxnID,User,Book,IssueDate,DueDate,ReturnDate,Fee,Status,DaysLate");
            for (Map<String,Object> m : txns) {
                String id = String.valueOf(m.get("id"));
                String user = m.get("user_name").toString();
                String book = m.get("book_title").toString();
                String issue = m.get("issue_date").toString();
                String due = m.get("due_date").toString();
                String ret = m.get("return_date") == null ? "" : m.get("return_date").toString();
                double fee = (double) m.get("fee");
                LocalDate dueD = LocalDate.parse(due);
                LocalDate rD = ret.isEmpty() ? LocalDate.now() : LocalDate.parse(ret);
                long daysLate = ChronoUnit.DAYS.between(dueD, rD);
                String status = ret.isEmpty() ? (daysLate>0?"OVERDUE":"ISSUED") : "RETURNED";
                lines.add(String.join(",", Arrays.asList(id, escapeCsv(user), escapeCsv(book), issue, due, ret, String.valueOf(fee), status, String.valueOf(Math.max(0, daysLate)))));
            }
            String filename = "late_fee_report_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".csv";
            Files.write(Path.of(filename), lines);
            JOptionPane.showMessageDialog(this, "Report generated: " + filename);
        } catch (Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, "Report failed: " + ex.getMessage()); }
    }

    private String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\n") || s.contains("\r") || s.contains("\"")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }
}
