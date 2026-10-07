# Bargain Testing Guide

## Overview
The bargain feature lets a user start a bargain and invite friends to help cut the price; once the target price is reached, the user can buy the item at the discounted price.

## How to simulate a successful bargain

### Option 1: several accounts help (recommended)

1. **Start a bargain**
   - Sign in as account A
   - Open a product page
   - Click "🔪 Bargain"
   - The system creates the bargain and opens the bargain page

2. **Invite friends to help**
   - Copy the bargain link (click "📤 Share with Friends")
   - Sign out of account A

3. **A friend helps cut the price**
   - Sign in as account B
   - Open the bargain link in the browser
   - Click "🎁 Help Cut the Price"
   - The system cuts a random amount

4. **Invite more friends**
   - Repeat step 3 with accounts C, D, E and so on
   - Each account's help lowers the price by a random amount
   - When the current price <= the target price, the bargain succeeds!

5. **After a successful bargain**
   - The page shows "🎉 Bargain successful!"
   - Click "💰 Buy Now"
   - Buy the item at the bargained price

### Option 2: adjust backend parameters (development testing)

To test a successful bargain quickly, change the backend parameters:

1. **Increase the cut per help**
   - Open `BargainService.java`
   - Find the cut calculation in the `helpBargain` method
   - Increase the amount cut each time, for example:
   ```java
   // Before: a random cut of 0.01-1.00 each time
   BigDecimal cutAmount = BigDecimal.valueOf(0.01 + Math.random() * 0.99);
   
   // After: a random cut of 5-20 each time (succeeds more easily)
   BigDecimal cutAmount = BigDecimal.valueOf(5 + Math.random() * 15);
   ```

2. **Need fewer helpers**
   - Change the target price so the gap from the original price is small
   - For example: original price 100, target 95, so only 5 needs to be cut

3. **Adjust the duration**
   - Change the expiry time in the `startBargain` method
   ```java
   // Default 24 hours
   expireTime = LocalDateTime.now().plusHours(24);
   
   // Can be made longer for testing
   expireTime = LocalDateTime.now().plusDays(7);
   ```

### Option 3: edit the database directly (fastest)

The fastest way to test is to change the database directly:

1. Connect to the database
2. Find your bargain record
3. Run the SQL:
```sql
-- Assuming the bargain ID is 1
UPDATE bargain_activity 
SET current_price = target_price, 
    status = 'SUCCESS' 
WHERE id = 1;
```

4. Refresh the bargain page to see the successful state

## Bargain logic

### Price calculation
- **Original price (originalPrice)**: the product's original price
- **Target price (targetPrice)**: the price after a successful bargain (usually 70-90% of the original)
- **Current price (currentPrice)**: the live price, which drops after each help

### Success condition
```
The bargain succeeds when current price <= target price
```

### Help rules
1. Each user can help each bargain only once
2. Each help lowers the price by a random amount
3. The person who started the bargain cannot help themselves
4. Nobody can help once the bargain has expired or succeeded

### Status transitions
```
ONGOING (in progress) → SUCCESS (succeeded) or EXPIRED (expired)
```

## Frontend features

### Bargain button style
- Gradient background (red-orange)
- Shadow and lift effect on hover
- Ripple animation on click
- Knife emoji icon

### Bargain page
- Shows the current price in real time
- A progress bar shows bargain progress
- The helper list shows every friend who helped
- A countdown shows the time left
- A buy button appears after success

## Testing tips

### Full test flow
1. Prepare 3-5 test accounts
2. Start a bargain with account 1
3. Help with the other accounts in turn
4. Watch the price and progress bar change
5. Check the purchase flow after success

### Edge cases
- [ ] Can a user help themselves? (should not)
- [ ] Can the same user help more than once? (should not)
- [ ] Can anyone help after the bargain expires? (should not)
- [ ] Can anyone help after the bargain succeeds? (should not)
- [ ] Can guests help? (depends on the implementation)

## FAQ

**Q: Why does nothing happen when I click "Help Cut the Price"?**
A: Check:
- Whether you have already helped this bargain
- Whether you started the bargain yourself
- Whether the bargain has expired or already succeeded
- Whether the browser console shows errors

**Q: How do I see every bargain?**
A: Add a bargain list page that shows every bargain the user has taken part in

**Q: How do I buy after a successful bargain?**
A: Click "Buy Now" to go to the product page and buy at the bargained price

## Development tips

To debug, check the following in the browser console:
```javascript
// The current bargain data
console.log(bargainActivity.value)

// The helper list
console.log(helpList.value)

// Whether the current user started the bargain
console.log(isOwner.value)
```

