use jni::JNIEnv;
use jni::objects::{JClass, JIntArray};
use jni::sys::{jint, jintArray};
use std::collections::{BinaryHeap, HashMap, HashSet};
use std::cmp::Ordering;

#[derive(Copy, Clone, Eq, PartialEq, Hash, Debug)]
struct Pos { x: i32, y: i32, z: i32 }

#[derive(Copy, Clone, PartialEq)]
struct State { cost: f32, pos: Pos }

impl Eq for State {}
impl Ord for State {
    fn cmp(&self, other: &Self) -> Ordering {
        other.cost.partial_cmp(&self.cost)
            .unwrap_or(Ordering::Equal)
    }
}
impl PartialOrd for State {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

fn heuristic(a: Pos, b: Pos) -> f32 {
    let dx = (a.x - b.x) as f32;
    let dy = (a.y - b.y) as f32;
    let dz = (a.z - b.z) as f32;
    (dx*dx + dy*dy + dz*dz).sqrt()
}

fn neighbors(pos: Pos, walkable: &HashSet<Pos>) -> Vec<Pos> {
    let dirs: &[(i32,i32,i32)] = &[
        (1,0,0),(-1,0,0),(0,0,1),(0,0,-1),
        (1,0,1),(-1,0,1),(1,0,-1),(-1,0,-1),
        (0,1,0),(0,-1,0)
    ];
    let mut result = Vec::new();
    for (dx,dy,dz) in dirs {
        let next = Pos { x: pos.x + dx, y: pos.y + dy, z: pos.z + dz };
        let floor = Pos { x: next.x, y: next.y - 1, z: next.z };
        let head = Pos { x: next.x, y: next.y + 1, z: next.z };
        if walkable.contains(&floor) && !walkable.contains(&next) && !walkable.contains(&head) {
            result.push(next);
        }
    }
    result
}

#[no_mangle]
pub extern "system" fn Java_com_sylvanforager_autotreeleller_navigation_PathfindingBridge_findPath(
    mut env: JNIEnv,
    _class: JClass,
    walkable_blocks: JIntArray,
    start_arr: JIntArray,
    goal_arr: JIntArray,
) -> jintArray {
    let walkable_len = env.get_array_length(&walkable_blocks).unwrap() as usize;
    let start_len = env.get_array_length(&start_arr).unwrap() as usize;
    let goal_len = env.get_array_length(&goal_arr).unwrap() as usize;

    let mut walkable_vec = vec![0i32; walkable_len];
    let mut start_vec = vec![0i32; start_len];
    let mut goal_vec = vec![0i32; goal_len];

    env.get_int_array_region(&walkable_blocks, 0, &mut walkable_vec).unwrap();
    env.get_int_array_region(&start_arr, 0, &mut start_vec).unwrap();
    env.get_int_array_region(&goal_arr, 0, &mut goal_vec).unwrap();

    let mut walkable_set = HashSet::new();
    let mut i = 0;
    while i + 2 < walkable_vec.len() {
        walkable_set.insert(Pos { x: walkable_vec[i], y: walkable_vec[i+1], z: walkable_vec[i+2] });
        i += 3;
    }

    let start = Pos { x: start_vec[0], y: start_vec[1], z: start_vec[2] };
    let goal = Pos { x: goal_vec[0], y: goal_vec[1], z: goal_vec[2] };

    let mut open: BinaryHeap<State> = BinaryHeap::new();
    let mut came_from: HashMap<Pos, Pos> = HashMap::new();
    let mut g_score: HashMap<Pos, f32> = HashMap::new();

    g_score.insert(start, 0.0);
    open.push(State { cost: heuristic(start, goal), pos: start });

    let mut found = false;
    while let Some(State { pos, .. }) = open.pop() {
        if pos == goal { found = true; break; }
        let g = *g_score.get(&pos).unwrap_or(&f32::MAX);
        for next in neighbors(pos, &walkable_set) {
            let step = if next.y != pos.y { 1.4 } else { 1.0 };
            let new_g = g + step as f32;
            if new_g < *g_score.get(&next).unwrap_or(&f32::MAX) {
                g_score.insert(next, new_g);
                came_from.insert(next, pos);
                let f = new_g + heuristic(next, goal);
                open.push(State { cost: f, pos: next });
            }
        }
    }

    let mut path: Vec<i32> = Vec::new();
    if found {
        let mut cur = goal;
        let mut steps = Vec::new();
        while cur != start {
            steps.push(cur);
            cur = *came_from.get(&cur).unwrap();
        }
        steps.reverse();
        for p in steps {
            path.push(p.x);
            path.push(p.y);
            path.push(p.z);
        }
    }

    let out = env.new_int_array(path.len() as i32).unwrap();
    if !path.is_empty() {
        env.set_int_array_region(&out, 0, &path).unwrap();
    }
    out.into_raw()
}