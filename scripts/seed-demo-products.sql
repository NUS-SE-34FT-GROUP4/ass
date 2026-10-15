-- Demo product catalog: twelve campus secondhand items with relevant product
-- art. Team decision 10/14: demo product photos must match the items being
-- sold (the old landscape-photo placeholders read as irrelevant).
--
-- The images ship inside the frontend bundle (frontend/public/demo/products/)
-- and are served same-origin through CloudFront on staging and the gateway
-- locally, so this seed needs no MinIO uploads and works in every environment.
--
-- Idempotent: fixed IDs 9001-9012; re-running refreshes the same rows.
-- Sellers reuse the seed accounts (seller_lvl1-5, IDs 101-105, password
-- admin123) so demo buyers can chat with, review and bargain against them.
--
-- Direct DB writes bypass the product-event bus, so rebuild the search
-- index after seeding or the new items will not be searchable.
--
-- Apply: mysql -h <host> -u <user> -p < scripts/seed-demo-products.sql
-- (staging: the same channel used for grants.sql; the core_svc user may
--  write products, otherwise run as an admin user)

USE trade;

DELETE FROM pms_product_media WHERE product_id BETWEEN 9001 AND 9012;
DELETE FROM pms_product WHERE id BETWEEN 9001 AND 9012;

INSERT INTO pms_product
    (id, user_id, name, description, price, stock, condition_level, location, category, status)
VALUES
    (9001, 101, 'CS2103T Software Engineering Textbook',
     'Latest edition, all chapters annotated lightly in pencil. Pick-up at NUS Kent Ridge or meet at PGP foyer.', 18.00, 1, 8, 'Kent Ridge', 'textbook', 1),
    (9002, 102, 'MacBook Air M1 (2020), 8GB / 256GB',
     '34 battery cycles, always used with a case. Includes original charger and a new sleeve.', 780.00, 1, 8, 'Bukit Timah', 'computer', 1),
    (9003, 103, 'iPhone 12 128GB - Blue',
     'Battery health 87 percent, no scratches on the back glass, screen protector on since day one. Comes with cable.', 420.00, 1, 7, 'Jurong East', 'phone', 1),
    (9004, 104, 'Fujifilm Instax Mini 11 Instant Camera',
     'Barely used, gift that duplicated an existing one. Two unopened film packs included.', 55.00, 1, 9, 'Clementi', 'camera', 1),
    (9005, 105, 'Nintendo Switch OLED - White',
     'Bought last semester for a group project demo, no longer needed. Box, dock and both Joy-Cons included.', 310.00, 1, 9, 'Punggol', 'gaming', 1),
    (9006, 101, 'Foldable Commuter Bicycle, 6-Speed',
     'Rides smoothly on MRT last-mile routes. Both folding joints work as they should; front and rear reflectors fitted.', 120.00, 1, 7, 'Ang Mo Kio', 'bicycle', 1),
    (9007, 102, 'Ergonomic Study Chair with Lumbar Support',
     'Height and armrest adjustable, mesh back. Disassembles to fit a Gojek boot.', 45.00, 1, 8, 'Queenstown', 'furniture', 1),
    (9008, 103, '87-Key Mechanical Keyboard, Brown Switches',
     'Hot-swappable board with PBT keycaps and a coiled cable. Great for labs and essays.', 60.00, 1, 9, 'Tampines', 'accessories', 1),
    (9009, 104, 'Yamaha F310 Acoustic Guitar',
     'New strings fitted last month, action comfortable for beginners. Includes soft case and capo.', 95.00, 1, 8, 'Woodlands', 'music', 1),
    (9010, 105, 'Adjustable Dumbbell Set, 20kg Pair',
     'Plates swap between 2kg and 10kg per hand. Rubber-lined so no floor marks in a hostel room.', 70.00, 1, 8, 'Bedok', 'fitness', 1),
    (9011, 101, 'The Lord of the Rings Box Set (3 Volumes)',
     'Paperback box set, spines uncreased, maps intact. Moving overseas and cannot ship these.', 25.00, 1, 9, 'Serangoon', 'literature', 1),
    (9012, 102, 'Dell 24-inch IPS Monitor, 1080p 75Hz',
     'Thin bezels, HDMI and DisplayPort, flicker-free. Two years old, panel has no dead pixels.', 110.00, 1, 9, 'Novena', 'computer', 1);

INSERT INTO pms_product_media (product_id, url, media_type, sort_order) VALUES
    (9001, '/demo/products/textbook.svg', 1, 0),
    (9002, '/demo/products/macbook.svg', 1, 0),
    (9003, '/demo/products/iphone.svg', 1, 0),
    (9004, '/demo/products/instax.svg', 1, 0),
    (9005, '/demo/products/switch.svg', 1, 0),
    (9006, '/demo/products/bicycle.svg', 1, 0),
    (9007, '/demo/products/chair.svg', 1, 0),
    (9008, '/demo/products/keyboard.svg', 1, 0),
    (9009, '/demo/products/guitar.svg', 1, 0),
    (9010, '/demo/products/dumbbell.svg', 1, 0),
    (9011, '/demo/products/rings.svg', 1, 0),
    (9012, '/demo/products/monitor.svg', 1, 0);
