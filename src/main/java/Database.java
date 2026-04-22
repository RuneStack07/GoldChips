import com.mongodb.client.*;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import com.google.gson.Gson;

public class Database {
    private static MongoCollection<Document> userCollection;
    private static final Gson gson = new Gson();
    public static final double RAKEBACK_PERCENTAGE = 0.006;

    // Database Initialize karne ke liye
    public static void init(String uri) {
        try {
            MongoClient mongoClient = MongoClients.create(uri);
            // Yahan humne database ka naam change kar diya hai
            MongoDatabase db = mongoClient.getDatabase("gold_chips_db");
            userCollection = db.getCollection("users");
            System.out.println("✅ Connected to Gold Chips Database successfully!");
        } catch (Exception e) {
            System.err.println("❌ MongoDB Connection Failed: " + e.getMessage());
        }
    }

    public static UserData getUserData(String userId) {
        if (userCollection == null) return new UserData();

        Document doc = userCollection.find(new Document("_id", userId)).first();
        if (doc == null) {
            UserData newData = new UserData();
            saveUserData(userId, newData);
            return newData;
        }
        return gson.fromJson(doc.toJson(), UserData.class);
    }

    public static void saveUserData(String userId, UserData data) {
        if (userCollection == null) return;
        if (data.balance < -0.0001) data.balance = 0;

        Document doc = Document.parse(gson.toJson(data));
        doc.put("_id", userId);
        userCollection.replaceOne(new Document("_id", userId), doc, new ReplaceOptions().upsert(true));
    }

    public static void updateWagerAndRakeback(String userId, double betAmount) {
        UserData ud = getUserData(userId);
        ud.wagered += betAmount;
        ud.rakeback += (betAmount * RAKEBACK_PERCENTAGE);
        saveUserData(userId, ud);
    }
}