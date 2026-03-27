const mineflayer = require('mineflayer')
const { pathfinder, Movements, goals } = require('mineflayer-pathfinder')
const { GoalBlock, GoalNear } = goals
const Vec3 = require('vec3')

// ─── Constants ────────────────────────────────────────────────────────────────

const TREE_BLOCKS = new Set(['oak_log','birch_log','spruce_log','jungle_log','acacia_log','dark_oak_log','mangrove_log','cherry_log'])
const LEAF_BLOCKS = new Set(['oak_leaves','birch_leaves','spruce_leaves','jungle_leaves','acacia_leaves','dark_oak_leaves','mangrove_leaves','cherry_leaves'])
const AOTV_NAME = 'Aspect of the Void'
const SCAN_RADIUS = 10 // max horizontal blocks to scan for trees
const MAX_TREE_DIST = 12 // FIX: ignore trees beyond this — stops far-POV locking
const CHOP_REACH = 4.5 // max reach to swing axe

// ─── AOTV Detection ────────────────────────────────────────────────────────────

function getAOTV(bot) {
 for (const item of bot.inventory.items()) {
 if (
 item &&
 item.customName &&
 item.customName.includes(AOTV_NAME)
 ) return item
 }
 // Also check hotbar by slot 0-8
 for (let slot = 36; slot <= 44; slot++) {
 const item = bot.inventory.slots[slot]
 if (
 item &&
 item.customName &&
 item.customName.includes(AOTV_NAME)
 ) return item
 }
 return null
}

async function equipAOTV(bot) {
 const aotv = getAOTV(bot)
 if (!aotv) {
 console.warn('[AOTV] Aspect of the Void not found in hotbar/inventory')
 return false
 }
 if (bot.heldItem && bot.heldItem.slot === aotv.slot) return true // already equipped
 await bot.equip(aotv, 'hand')
 await bot.waitForTicks(2)
 return true
}

// ─── Tree Detection ───────────────────────────────────────────────────────────

// FIX: isTreeBlock now requires the log to actually exist, not just line-of-sight
function isLogBlock(block) {
 return block && TREE_BLOCKS.has(block.name)
}

// FIX: Validate the tree is real and within max distance — kills far-POV locking
function findNearestTree(bot) {
 const pos = bot.entity.position.floored()
 let nearest = null
 let nearestDist = Infinity

 for (let dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
 for (let dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
 for (let dy = -2; dy <= 8; dy++) {
 const check = pos.offset(dx, dy, dz)
 const block = bot.blockAt(check)
 if (!isLogBlock(block)) continue

 const dist = Math.sqrt(dx*dx + dz*dz + dy*dy)

 // FIX: Hard cap — ignore logs that are too far away
 if (dist > MAX_TREE_DIST) continue

 if (dist < nearestDist) {
 nearestDist = dist
 nearest = block
 }
 }
 }
 }
 return nearest
}

// FIX: Confirm tree still exists before chopping — prevents phantom jumping
function treeStillExists(bot, basePos) {
 const block = bot.blockAt(basePos)
 return isLogBlock(block)
}

// Find bottom log of a tree column (walk down until non-log)
function findTreeBase(bot, anyLogPos) {
 let base = anyLogPos.clone()
 while (true) {
 const below = base.offset(0, -1, 0)
 const block = bot.blockAt(below)
 if (isLogBlock(block)) {
 base = below
 } else {
 break
 }
 }
 return base
}

// Collect all log positions in tree (BFS upward)
function getTreeLogs(bot, basePos) {
 const logs = []
 const visited = new Set()
 const queue = [basePos.clone()]

 while (queue.length) {
 const cur = queue.shift()
 const key = `${cur.x},${cur.y},${cur.z}`
 if (visited.has(key)) continue
 visited.add(key)

 const block = bot.blockAt(cur)
 if (!isLogBlock(block)) continue

 logs.push(cur.clone())

 // Check all 6 neighbors + diagonals for connected logs
 for (const offset of [
 [0,1,0],[0,-1,0],[1,0,0],[-1,0,0],[0,0,1],[0,0,-1],
 [1,1,0],[-1,1,0],[0,1,1],[0,1,-1]
 ]) {
 queue.push(cur.offset(...offset))
 }
 }
 return logs
}

// ─── Pathfinder Helpers ────────────────────────────────────────────────────────

async function walkToBlock(bot, targetPos) {
 const goal = new GoalNear(targetPos.x, targetPos.y, targetPos.z, 2)
 try {
 await bot.pathfinder.goto(goal)
 } catch (e) {
 console.warn('[Path] Could not reach target:', e.message)
 }
}

async function equipBestAxe(bot) {
 const axes = ['diamond_axe','golden_axe','iron_axe','stone_axe','wooden_axe']
 for (const axeName of axes) {
 const item = bot.inventory.items().find(i => i.name === axeName)
 if (item) {
 await bot.equip(item, 'hand')
 return true
 }
 }
 return false
}

// ─── Chop Logic ────────────────────────────────────────────────────────────────

async function chopLog(bot, logPos) {
 // FIX: Check block still exists before swinging
 if (!treeStillExists(bot, logPos)) return

 const dist = bot.entity.position.distanceTo(logPos.offset(0.5, 0.5, 0.5))

 // Walk closer if out of reach
 if (dist > CHOP_REACH) {
 await walkToBlock(bot, logPos)
 await bot.waitForTicks(3)
 }

 // FIX: Re-verify after walking — block might have been removed by now
 if (!treeStillExists(bot, logPos)) return

 await bot.lookAt(logPos.offset(0.5, 0.5, 0.5), true)
 await bot.waitForTicks(1)

 const block = bot.blockAt(logPos)
 if (!block || !isLogBlock(block)) return // double guard

 await bot.dig(block)
 await bot.waitForTicks(2)
}

// ─── Etherwarp ────────────────────────────────────────────────────────────────

async function etherwarp(bot, targetPos) {
 const equipped = await equipAOTV(bot)
 if (!equipped) {
 console.warn('[Etherwarp] No AOTV found — falling back to walk')
 await walkToBlock(bot, targetPos)
 return
 }

 const dist = bot.entity.position.distanceTo(targetPos)
 if (dist > 60) {
 console.warn('[Etherwarp] Target out of range, walking instead')
 await walkToBlock(bot, targetPos)
 return
 }

 // Look at target block
 await bot.lookAt(targetPos.offset(0.5, 0, 0.5), true)
 await bot.waitForTicks(3 + Math.floor(Math.random() * 2))

 bot.activateItem()
 await bot.waitForTicks(6)

 // Verify warp succeeded
 if (bot.entity.position.distanceTo(targetPos) > 4) {
 console.warn('[Etherwarp] Warp failed — walking fallback')
 await walkToBlock(bot, targetPos)
 }
}

// ─── Farm Loop ────────────────────────────────────────────────────────────────

let isFarming = false

async function farmTreeLoop(bot) {
 if (isFarming) return
 isFarming = true

 console.log('[Farm] Starting tree farm loop')

 while (isFarming) {
 // FIX: Find nearest REAL tree within MAX_TREE_DIST
 const nearestLog = findNearestTree(bot)

 if (!nearestLog) {
 console.log('[Farm] No trees found nearby — waiting 2s')
 await bot.waitForTicks(40)
 continue
 }

 const base = findTreeBase(bot, nearestLog.position)
 const logs = getTreeLogs(bot, base)

 if (logs.length === 0) {
 await bot.waitForTicks(5)
 continue
 }

 console.log(`[Farm] Found tree at ${base} with ${logs.length} logs`)
 await equipBestAxe(bot)

 // Chop bottom-up so gravity drops top logs
 for (const logPos of logs.sort((a, b) => a.y - b.y)) {
 // FIX: Always verify block exists before each chop — stops phantom jumping
 if (!treeStillExists(bot, logPos)) continue
 await chopLog(bot, logPos)
 }

 // Short pause between trees
 await bot.waitForTicks(10 + Math.floor(Math.random() * 10))
 }
}

// ─── Bot Setup ────────────────────────────────────────────────────────────────

function createBot() {
 const bot = mineflayer.createBot({
 host: 'mc.hypixel.net',
 username: 'your_email@gmail.com',
 auth: 'microsoft',
 version: '1.21.1',
 })

 bot.loadPlugin(pathfinder)

 bot.once('spawn', async () => {
 const mcData = require('minecraft-data')(bot.version)
 const movements = new Movements(bot, mcData)
 movements.canDig = true
 movements.allow1by1towers = false
 bot.pathfinder.setMovements(movements)

 await bot.waitForTicks(20)

 // Log AOTV detection result on spawn
 const aotv = getAOTV(bot)
 if (aotv) {
 console.log(`[AOTV] Found: "${aotv.customName}" in slot ${aotv.slot}`)
 } else {
 console.warn('[AOTV] Aspect of the Void not found — etherwarp disabled')
 }

 farmTreeLoop(bot)
 })

 bot.on('chat', (username, message) => {
 if (message === 'stop') {
 isFarming = false
 bot.pathfinder.stop()
 console.log('[Farm] Stopped by chat command')
 }
 if (message === 'etherwarp test') {
 const target = bot.entity.position.offset(5, 0, 5)
 etherwarp(bot, target)
 }
 })

 bot.on('end', () => {
 isFarming = false
 setTimeout(createBot, 5000 + Math.random() * 3000)
 })

 bot.on('error', (err) => console.error('[Error]', err))
 return bot
}

createBot()