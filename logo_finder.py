import streamlit as st
import torch
import clip
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from tqdm import tqdm
from ultralytics import YOLO
from segment_anything import sam_model_registry, SamPredictor
import os, csv, io
import faiss

# Set Streamlit layout
st.set_page_config(layout="wide", page_title="Image-to-Video Logo Search App")

# --------------------------- #
# --- Helper Functions ------#
# --------------------------- #


@st.cache_resource(show_spinner=False)
def load_clip_model():
    model, preprocess = clip.load("ViT-L/14", device=device)
    return model, preprocess


@st.cache_resource(show_spinner=False)
def load_yolo_model():
    return YOLO("yolov8n.pt")


@st.cache_resource(show_spinner=False)
def load_sam_model(checkpoint_path):
    sam = sam_model_registry["vit_b"](checkpoint=checkpoint_path).to(device)
    return sam, SamPredictor(sam)


def get_embedding(image, clip_model, preprocess):
    image_input = preprocess(image).unsqueeze(0).to(device)
    with torch.no_grad():
        emb = clip_model.encode_image(image_input)
        emb /= emb.norm(dim=-1, keepdim=True)
    return emb


# New batch embedding function
def get_batch_embeddings(images, clip_model, preprocess, device="cpu", batch_size=16):
    embeddings = []
    with torch.no_grad():
        for i in range(0, len(images), batch_size):
            batch_imgs = images[i : i + batch_size]
            batch_tensor = torch.stack([preprocess(img) for img in batch_imgs]).to(
                device
            )
            batch_emb = clip_model.encode_image(batch_tensor)
            batch_emb /= batch_emb.norm(dim=-1, keepdim=True)
            embeddings.append(batch_emb.cpu().numpy())
    return np.vstack(embeddings).astype("float32")


def segment_query(query_cv2, sam_predictor, yolo_model):
    yolo_results = yolo_model.predict(query_cv2, verbose=False)[0]
    if yolo_results.boxes is not None and len(yolo_results.boxes) > 0:
        x1, y1, x2, y2 = map(int, yolo_results.boxes.xyxy[0].tolist())
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        input_point = np.array([[cx, cy]])
        input_label = np.array([1])
    else:
        input_point = None
        input_label = None

    sam_predictor.set_image(query_cv2)
    masks, _, _ = sam_predictor.predict(
        point_coords=input_point, point_labels=input_label, multimask_output=False
    )
    query_mask = masks[0]
    segmented = query_cv2 * query_mask[..., None]
    return Image.fromarray(segmented)


def sample_frames(video_path, interval=1):
    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS)
    step = int(fps * interval)
    frames = []
    timestamps = []
    i = 0

    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break
        if i % step == 0:
            frames.append(frame)
            timestamps.append(cap.get(cv2.CAP_PROP_POS_MSEC) / 1000.0)
        i += 1
    cap.release()
    return frames, timestamps


def get_sliding_window_patches(frame, sizes=[128, 224, 320], strides=[64, 112, 160]):
    h, w, _ = frame.shape
    patches = []
    boxes = []
    for size, stride in zip(sizes, strides):
        for y in range(0, h - size + 1, stride):
            for x in range(0, w - size + 1, stride):
                crop = frame[y : y + size, x : x + size]
                patch = Image.fromarray(cv2.cvtColor(crop, cv2.COLOR_BGR2RGB))
                patches.append(patch)
                boxes.append((x, y, x + size, y + size))
    return patches, boxes


def overlay_boxes(image, boxes, scores, source_labels):
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default()
    for box, score, label in zip(boxes, scores, source_labels):
        x1, y1, x2, y2 = box
        draw.rectangle([x1, y1, x2, y2], outline="red", width=2)
        text = f"{label}:{score:.2f}"
        draw.text((x1, y1 - 10), text, fill="red", font=font)
    return image


def update_faiss_index(embeddings):
    dim = embeddings.shape[1]
    index = faiss.IndexFlatIP(dim)
    index.add(embeddings)
    return index


# --------------------------- #
# --- Main Streamlit App ----#
# --------------------------- #

st.title("Image-to-Video Logo Search App")
st.markdown(
    "Upload a query image (e.g. a logo) and a video. The app will search for frames where the logo appears."
)

device = "cuda" if torch.cuda.is_available() else "cpu"

# Sidebar options
st.sidebar.header("Settings")
sim_threshold = st.sidebar.slider(
    "Similarity Threshold", min_value=0.0, max_value=1.0, value=0.15, step=0.05
)
top_k = st.sidebar.number_input("Top K Candidates per Frame", min_value=1, value=3)

# Upload files
query_file = st.file_uploader("Upload Query Image", type=["jpg", "png"])
video_file = st.file_uploader("Upload Video", type=["mp4", "avi", "mov"])

sam_checkpoint = st.text_input("SAM Checkpoint Path", value="sam_vit_b_01ec64.pth")


def display_sample_frames(frames):
    st.markdown(
        """
        <style>
        .scrollable-frames {
            display: flex;
            overflow-x: scroll;
            padding: 10px 0;
        }
        .scrollable-frames img {
            height: 180px;
            margin-right: 10px;
            border-radius: 8px;
        }
        </style>
        """,
        unsafe_allow_html=True,
    )

    st.markdown('<div class="scrollable-frames">', unsafe_allow_html=True)
    for frame in frames:
        frame_image = Image.fromarray(frame)
        st.image(frame_image, use_column_width=False, width=200)
    st.markdown("</div>", unsafe_allow_html=True)


if query_file and video_file and sam_checkpoint:
    query_bytes = np.asarray(bytearray(query_file.read()), dtype=np.uint8)
    query_cv2 = cv2.imdecode(query_bytes, cv2.IMREAD_COLOR)
    query_pil = Image.fromarray(cv2.cvtColor(query_cv2, cv2.COLOR_BGR2RGB))

    t_video = f"temp_video.{video_file.name.split('.')[-1]}"
    with open(t_video, "wb") as f:
        f.write(video_file.getbuffer())

    st.sidebar.info("Loading models...")
    clip_model, clip_preprocess = load_clip_model()
    yolo_model = load_yolo_model()
    sam_model, sam_predictor = load_sam_model(sam_checkpoint)

    st.sidebar.info("Segmenting query image...")
    seg_query = segment_query(query_cv2, sam_predictor, yolo_model)
    if np.sum(np.array(seg_query)) == 0:
        st.warning("SAM segmentation failed, using original query image.")
        seg_query = query_pil

    st.image(seg_query, caption="Segmented Query", use_column_width=True)
    query_embedding = get_embedding(seg_query, clip_model, clip_preprocess)

    st.sidebar.info("Sampling video frames...")
    frames, timestamps = sample_frames(t_video, interval=80)

    st.header("Browse Sample Frames")
    display_sample_frames(frames)

    results = []
    display_results = []
    progress_bar = st.progress(0)
    num_frames = len(frames)

    for i, frame in enumerate(tqdm(frames, desc="Processing frames")):
        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        frame_pil = Image.fromarray(frame_rgb)

        candidate_images = []
        candidate_boxes = []
        candidate_sources = []

        yolo_results = yolo_model.predict(frame, verbose=False)[0]
        if yolo_results.boxes is not None:
            for box in yolo_results.boxes.xyxy.cpu().numpy():
                x1, y1, x2, y2 = map(int, box)
                crop = frame[y1:y2, x1:x2]
                pil_crop = Image.fromarray(cv2.cvtColor(crop, cv2.COLOR_BGR2RGB))
                candidate_images.append(pil_crop)
                candidate_boxes.append((x1, y1, x2, y2))
                candidate_sources.append("YOLO")

        patches, patch_boxes = get_sliding_window_patches(frame)
        candidate_images.extend(patches)
        candidate_boxes.extend(patch_boxes)
        candidate_sources.extend(["Window"] * len(patches))

        # Using the new batch embedding function
        embeddings = get_batch_embeddings(
            candidate_images, clip_model, clip_preprocess, device=device, batch_size=16
        )

        if len(embeddings) == 0:
            progress_bar.progress((i + 1) / num_frames)
            continue

        index = update_faiss_index(embeddings)
        query_np = query_embedding.cpu().numpy().astype("float32")
        D, I = index.search(query_np, k=min(top_k, embeddings.shape[0]))

        top_candidates = []
        for rank, idx in enumerate(I[0]):
            sim_score = float(D[0][rank])
            if sim_score >= sim_threshold:
                cand_box = candidate_boxes[idx]
                top_candidates.append((cand_box, sim_score, candidate_sources[idx]))
                results.append(
                    {
                        "timestamp": timestamps[i],
                        "frame_index": i,
                        "candidate_rank": rank + 1,
                        "similarity": sim_score,
                        "bounding_box": cand_box,
                        "source": candidate_sources[idx],
                    }
                )

        if top_candidates:
            boxes, scores, sources = zip(*top_candidates)
            vis_frame = overlay_boxes(frame_pil.copy(), boxes, scores, sources)
            display_results.append((timestamps[i], vis_frame))

        progress_bar.progress((i + 1) / num_frames)

    st.success("Processing complete!")

    csv_buffer = io.StringIO()
    fieldnames = [
        "timestamp",
        "frame_index",
        "candidate_rank",
        "similarity",
        "bounding_box",
        "source",
    ]
    writer = csv.DictWriter(csv_buffer, fieldnames=fieldnames)
    writer.writeheader()
    for row in results:
        writer.writerow(row)
    csv_bytes = csv_buffer.getvalue().encode("utf-8")

    st.download_button("Download CSV Results", csv_bytes, "results.csv", "text/csv")

    st.header("Matching Frames")
    for ts, img in display_results:
        st.markdown(f"**Timestamp:** {ts:.2f} s")
        st.image(img, use_column_width=True)

    os.remove(t_video)
