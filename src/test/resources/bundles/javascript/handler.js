function handler(ctx) {
    const response = `Hello from ${ctx.request().getParam("name")}`;
    ctx.json({ msg: response });
}

handler
